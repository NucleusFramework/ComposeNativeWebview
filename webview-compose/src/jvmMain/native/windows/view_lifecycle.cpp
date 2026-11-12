#include "compose_webview_internal.h"

#include <algorithm>
#include <cstdio>
#include <unordered_map>

using Microsoft::WRL::Callback;

/* ── WebView2Loader.dll ─────────────────────────────────────────────────── */

typedef HRESULT(STDMETHODCALLTYPE *PFN_CreateCoreWebView2EnvironmentWithOptions)(
    PCWSTR browserExecutableFolder,
    PCWSTR userDataFolder,
    ICoreWebView2EnvironmentOptions *environmentOptions,
    ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler *environmentCreatedHandler);

static PFN_CreateCoreWebView2EnvironmentWithOptions s_pCreateEnv = nullptr;

static bool ensureLoaderLoaded() {
    if (s_pCreateEnv) return true;
    HMODULE self = nullptr;
    GetModuleHandleExW(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS,
                       reinterpret_cast<LPCWSTR>(&ensureLoaderLoaded), &self);
    if (self) {
        wchar_t selfPath[MAX_PATH];
        if (GetModuleFileNameW(self, selfPath, MAX_PATH) > 0) {
            for (int i = (int)wcslen(selfPath) - 1; i >= 0; --i) {
                if (selfPath[i] == L'\\' || selfPath[i] == L'/') {
                    selfPath[i] = 0;
                    break;
                }
            }
            SetDllDirectoryW(selfPath);
        }
    }
    HMODULE loader = LoadLibraryW(L"WebView2Loader.dll");
    if (!loader) return false;
    s_pCreateEnv = reinterpret_cast<PFN_CreateCoreWebView2EnvironmentWithOptions>(
        GetProcAddress(loader, "CreateCoreWebView2EnvironmentWithOptions"));
    return s_pCreateEnv != nullptr;
}

/* ── Handle map ─────────────────────────────────────────────────────────── */

static std::mutex g_handlesMutex;
static std::unordered_map<jlong, ComposeWebViewState *> g_handles;
static std::atomic<jlong> g_nextHandle{1};

ComposeWebViewState *compose_webview_state_from_handle(jlong handle) {
    std::lock_guard<std::mutex> lock(g_handlesMutex);
    auto it = g_handles.find(handle);
    return it == g_handles.end() ? nullptr : it->second;
}

jlong compose_webview_register(ComposeWebViewState *s) {
    jlong handle = g_nextHandle.fetch_add(1, std::memory_order_relaxed);
    s->handle = handle;
    std::lock_guard<std::mutex> lock(g_handlesMutex);
    g_handles[handle] = s;
    return handle;
}

ComposeWebViewState *compose_webview_unregister(jlong handle) {
    std::lock_guard<std::mutex> lock(g_handlesMutex);
    auto it = g_handles.find(handle);
    if (it == g_handles.end()) return nullptr;
    ComposeWebViewState *s = it->second;
    g_handles.erase(it);
    return s;
}

/* ── Helpers ────────────────────────────────────────────────────────────── */

/* The module WebView2's objects come from, known once it has handed one over. */
static HMODULE g_engineModule = nullptr;

/* The module holding [address]. */
static HMODULE moduleAt(const void *address) {
    HMODULE module = nullptr;
    if (!address) return nullptr;
    GetModuleHandleExW(
        GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS | GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,
        reinterpret_cast<LPCWSTR>(address), &module);
    return module;
}

static BOOL CALLBACK addWindow(HWND window, LPARAM windows) {
    reinterpret_cast<std::vector<HWND> *>(windows)->push_back(window);
    return TRUE;
}

/* The calling thread's windows: its top-level ones, those inside them, and its message-only ones. */
static std::vector<HWND> threadWindows() {
    const DWORD thread = GetCurrentThreadId();
    std::vector<HWND> windows;
    EnumThreadWindows(thread, addWindow, reinterpret_cast<LPARAM>(&windows));
    const size_t topLevel = windows.size();
    for (size_t i = 0; i < topLevel; i++) {
        EnumChildWindows(windows[i], addWindow, reinterpret_cast<LPARAM>(&windows));
    }
    for (HWND w = nullptr; (w = FindWindowExW(HWND_MESSAGE, w, nullptr, nullptr)) != nullptr;) {
        windows.push_back(w);
    }
    windows.erase(
        std::remove_if(windows.begin(), windows.end(), [thread](HWND w) {
            return GetWindowThreadProcessId(w, nullptr) != thread;
        }),
        windows.end());
    return windows;
}

/*
 * Whether [window] is one of WebView2's: of a class whose procedure is in WebView2's module. Until
 * its first environment tells which module that is, a window is WebView2's when the call being
 * waited on made it, so it is not among [before], the thread's windows from before that call.
 */
static bool isEngineWindow(HWND window, const std::vector<HWND> &before) {
    if (g_engineModule) {
        const ULONG_PTR classProcedure = GetClassLongPtrW(window, GCLP_WNDPROC);
        return moduleAt(reinterpret_cast<const void *>(classProcedure)) == g_engineModule;
    }
    return std::find(before.begin(), before.end(), window) == before.end();
}

/*
 * Waits until [done]: WebView2 finishes an asynchronous call through the calling thread's message
 * queue. Only what is queued for WebView2's own windows is dispatched meanwhile. Everything else on
 * the thread is the host's and stays queued for its loop. The host is in the middle of the call
 * that waits here, and can only half act on a message handed to it now: its handlers would run
 * inside that call, and its event loop sets aside a wake-up or a request to redraw it receives
 * while busy, then sleeps waiting for the one that was taken off the queue here.
 */
void compose_webview_pump_until_done(const std::vector<HWND> &before, const std::atomic<bool> &done) {
    const HWND threadMessages = reinterpret_cast<HWND>(static_cast<LONG_PTR>(-1));
    while (!done.load(std::memory_order_acquire)) {
        bool dispatched = false;
        MSG msg;
        /* Takes nothing off the queue: a call into it is what lets through what other threads sent. */
        PeekMessageW(&msg, threadMessages, 0, 0, PM_NOREMOVE);
        for (HWND window : threadWindows()) {
            if (!isEngineWindow(window, before)) continue;
            while (PeekMessageW(&msg, window, 0, 0, PM_REMOVE)) {
                DispatchMessageW(&msg);
                dispatched = true;
                if (done.load(std::memory_order_acquire)) return;
            }
        }
        if (!dispatched) MsgWaitForMultipleObjectsEx(0, nullptr, 5, QS_ALLINPUT, 0);
    }
}

/* Says on stderr which step of creating a view failed, and with what result. */
static void logCreateFailure(const char *step, HRESULT hr) {
    fprintf(stderr, "compose_webview: %s failed: 0x%08lX\n", step, static_cast<unsigned long>(hr));
    fflush(stderr);
}

/*
 * The DirectComposition tree of one host window. A window takes a single topmost composition target,
 * so the views in it share this one, each with a visual of its own under [root]. Touched on the
 * window's UI thread only, where views are created and released.
 */
struct WindowTree {
    ComPtr<IDCompositionDevice> device;
    ComPtr<IDCompositionTarget> target;
    ComPtr<IDCompositionVisual> root;
    int views = 0;
};
static std::unordered_map<HWND, WindowTree> g_windowTrees;

/* Gives [s] its visual in its window's tree, which the window's first view creates. */
static HRESULT joinWindowTree(ComposeWebViewState *s) {
    auto it = g_windowTrees.find(s->parent);
    if (it == g_windowTrees.end()) {
        WindowTree tree;
        HRESULT hr = DCompositionCreateDevice(nullptr, IID_PPV_ARGS(&tree.device));
        if (SUCCEEDED(hr)) hr = tree.device->CreateTargetForHwnd(s->parent, TRUE, &tree.target);
        if (SUCCEEDED(hr)) hr = tree.device->CreateVisual(&tree.root);
        if (SUCCEEDED(hr)) hr = tree.target->SetRoot(tree.root.Get());
        if (FAILED(hr)) return hr;
        it = g_windowTrees.emplace(s->parent, std::move(tree)).first;
    }
    WindowTree &tree = it->second;
    ComPtr<IDCompositionVisual> visual;
    HRESULT hr = tree.device->CreateVisual(&visual);
    if (SUCCEEDED(hr)) hr = tree.root->AddVisual(visual.Get(), TRUE, nullptr);
    if (FAILED(hr)) {
        if (tree.views == 0) g_windowTrees.erase(it);
        return hr;
    }
    tree.views++;
    s->dcompDevice = tree.device;
    s->rootVisual = visual;
    s->inWindowTree = true;
    return S_OK;
}

/* Takes [s]'s visual out of its window's tree; the window's last view takes the tree with it. */
static void leaveWindowTree(ComposeWebViewState *s) {
    if (!s->inWindowTree) return;
    s->inWindowTree = false;
    auto it = g_windowTrees.find(s->parent);
    if (it == g_windowTrees.end()) return;
    WindowTree &tree = it->second;
    if (s->rootVisual) tree.root->RemoveVisual(s->rootVisual.Get());
    if (--tree.views <= 0) {
        g_windowTrees.erase(it);
    } else {
        tree.device->Commit();
    }
}

void compose_webview_apply_rounded_clip(ComposeWebViewState &s) {
    if (!s.dcompDevice || !s.rootVisual) return;
    if (s.cornerRadiusPx <= 0.0f) {
        s.rootVisual->SetClip(static_cast<IDCompositionClip *>(nullptr));
        return;
    }
    ComPtr<IDCompositionRectangleClip> clip;
    if (FAILED(s.dcompDevice->CreateRectangleClip(&clip))) return;
    float w = static_cast<float>(s.widthPx);
    float h = static_cast<float>(s.heightPx);
    float r = s.cornerRadiusPx;
    float cap = (w < h ? w : h) * 0.5f;
    if (r > cap) r = cap;
    clip->SetLeft(0.0f);
    clip->SetTop(0.0f);
    clip->SetRight(w);
    clip->SetBottom(h);
    clip->SetTopLeftRadiusX(r);
    clip->SetTopLeftRadiusY(r);
    clip->SetTopRightRadiusX(r);
    clip->SetTopRightRadiusY(r);
    clip->SetBottomLeftRadiusX(r);
    clip->SetBottomLeftRadiusY(r);
    clip->SetBottomRightRadiusX(r);
    clip->SetBottomRightRadiusY(r);
    s.rootVisual->SetClip(clip.Get());
}

void compose_webview_apply_bounds(ComposeWebViewState &s) {
    if (s.controller) {
        RECT bounds = {s.xPx, s.yPx, s.xPx + s.widthPx, s.yPx + s.heightPx};
        s.controller->put_Bounds(bounds);
    }
    if (s.rootVisual) {
        s.rootVisual->SetOffsetX(static_cast<float>(s.xPx));
        s.rootVisual->SetOffsetY(static_cast<float>(s.yPx));
    }
}

std::wstring compose_webview_jstring_to_wide(JNIEnv *env, jstring s) {
    if (!s) return {};
    const jchar *chars = env->GetStringChars(s, nullptr);
    jsize len = env->GetStringLength(s);
    std::wstring out(reinterpret_cast<const wchar_t *>(chars),
                     reinterpret_cast<const wchar_t *>(chars) + len);
    env->ReleaseStringChars(s, chars);
    return out;
}

jstring compose_webview_wide_to_jstring(JNIEnv *env, const std::wstring &s) {
    return env->NewString(
        reinterpret_cast<const jchar *>(s.c_str()),
        static_cast<jsize>(s.size()));
}

std::string compose_webview_wide_to_utf8(const std::wstring &w) {
    if (w.empty()) return {};
    int n = WideCharToMultiByte(CP_UTF8, 0, w.c_str(), static_cast<int>(w.size()),
                                nullptr, 0, nullptr, nullptr);
    if (n <= 0) return {};
    std::string out(static_cast<size_t>(n), '\0');
    WideCharToMultiByte(CP_UTF8, 0, w.c_str(), static_cast<int>(w.size()),
                        out.data(), n, nullptr, nullptr);
    return out;
}

std::wstring compose_webview_utf8_to_wide(const std::string &u) {
    if (u.empty()) return {};
    int n = MultiByteToWideChar(CP_UTF8, 0, u.c_str(), static_cast<int>(u.size()),
                                nullptr, 0);
    if (n <= 0) return {};
    std::wstring out(static_cast<size_t>(n), L'\0');
    MultiByteToWideChar(CP_UTF8, 0, u.c_str(), static_cast<int>(u.size()),
                        out.data(), n);
    return out;
}

std::string compose_webview_json_escape(const std::string &s) {
    std::string out;
    out.reserve(s.size() + 8);
    for (unsigned char c : s) {
        switch (c) {
            case '"': out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\b': out += "\\b"; break;
            case '\f': out += "\\f"; break;
            case '\n': out += "\\n"; break;
            case '\r': out += "\\r"; break;
            case '\t': out += "\\t"; break;
            default:
                if (c < 0x20) {
                    char buf[8];
                    snprintf(buf, sizeof(buf), "\\u%04x", c);
                    out += buf;
                } else {
                    out += static_cast<char>(c);
                }
        }
    }
    return out;
}

/* ── Create / release ───────────────────────────────────────────────────── */

ComposeWebViewState *compose_webview_create(
    HWND parent,
    const ComposeWebViewCreateOptions &opts) {
    if (!ensureLoaderLoaded()) return nullptr;

    HRESULT coInitHr = CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED);
    (void)coInitHr;

    auto *s = new ComposeWebViewState();
    s->parent = parent;
    const std::vector<HWND> before = threadWindows();

    std::wstring userDataFolder = opts.dataDirectory;
    if (userDataFolder.empty() && opts.incognito) {
        wchar_t tempPath[MAX_PATH];
        GetTempPathW(MAX_PATH, tempPath);
        userDataFolder = std::wstring(tempPath) + L"compose_webview_incognito_" +
                         std::to_wstring(GetCurrentProcessId()) + L"_" +
                         std::to_wstring(GetTickCount64());
    }

    std::atomic<bool> envDone{false};
    HRESULT envResult = E_FAIL;
    HRESULT hr = s_pCreateEnv(
        nullptr,
        userDataFolder.empty() ? nullptr : userDataFolder.c_str(),
        nullptr,
        Callback<ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler>(
            [&](HRESULT result, ICoreWebView2Environment *env) -> HRESULT {
                envResult = result;
                if (SUCCEEDED(result) && env) {
                    s->env = env;
                    /* A COM object starts with the address of its table of methods, which is in
                     * the module that implements it. */
                    g_engineModule = moduleAt(*reinterpret_cast<void **>(env));
                }
                envDone.store(true, std::memory_order_release);
                return S_OK;
            }).Get());
    if (FAILED(hr)) {
        logCreateFailure("starting the WebView2 environment", hr);
        delete s;
        return nullptr;
    }
    compose_webview_pump_until_done(before, envDone);
    if (FAILED(envResult) || !s->env) {
        logCreateFailure("creating the WebView2 environment", envResult);
        delete s;
        return nullptr;
    }

    std::atomic<bool> ccDone{false};
    HRESULT ccResult = E_FAIL;
    ComPtr<ICoreWebView2Environment3> env3;
    if (FAILED(s->env.As(&env3)) || !env3) {
        logCreateFailure("getting ICoreWebView2Environment3", E_NOINTERFACE);
        delete s;
        return nullptr;
    }
    hr = env3->CreateCoreWebView2CompositionController(
        parent,
        Callback<ICoreWebView2CreateCoreWebView2CompositionControllerCompletedHandler>(
            [&](HRESULT result, ICoreWebView2CompositionController *cc) -> HRESULT {
                ccResult = result;
                if (SUCCEEDED(result) && cc) s->compController = cc;
                ccDone.store(true, std::memory_order_release);
                return S_OK;
            }).Get());
    if (FAILED(hr)) {
        logCreateFailure("starting the composition controller", hr);
        delete s;
        return nullptr;
    }
    compose_webview_pump_until_done(before, ccDone);
    if (FAILED(ccResult) || !s->compController) {
        logCreateFailure("creating the composition controller", ccResult);
        delete s;
        return nullptr;
    }

    if (FAILED(s->compController.As(&s->controller)) || !s->controller) {
        logCreateFailure("getting the controller", E_NOINTERFACE);
        delete s;
        return nullptr;
    }
    if (FAILED(s->controller->get_CoreWebView2(&s->webview)) || !s->webview) {
        logCreateFailure("getting the web view", E_FAIL);
        delete s;
        return nullptr;
    }
    s->webview.As(&s->webview2);
    if (s->webview2) {
        s->webview2->get_CookieManager(&s->cookieManager);
    }

    ComPtr<ICoreWebView2Settings> settings;
    if (SUCCEEDED(s->webview->get_Settings(&settings)) && settings) {
        settings->put_IsScriptEnabled(opts.javascriptEnabled ? TRUE : FALSE);
        settings->put_AreDevToolsEnabled(opts.enableDevtools ? TRUE : FALSE);
        settings->put_IsWebMessageEnabled(TRUE);
        settings->put_AreDefaultContextMenusEnabled(TRUE);
        settings->put_IsStatusBarEnabled(FALSE);
        if (!opts.userAgent.empty()) {
            ComPtr<ICoreWebView2Settings2> settings2;
            if (SUCCEEDED(settings.As(&settings2)) && settings2) {
                settings2->put_UserAgent(opts.userAgent.c_str());
            }
        }
    }

    ComPtr<ICoreWebView2Controller2> controller2;
    if (SUCCEEDED(s->controller.As(&controller2)) && controller2) {
        COREWEBVIEW2_COLOR c{};
        if (opts.transparent) {
            c.A = static_cast<BYTE>(opts.bgA * 255.f);
            c.R = static_cast<BYTE>(opts.bgR * 255.f);
            c.G = static_cast<BYTE>(opts.bgG * 255.f);
            c.B = static_cast<BYTE>(opts.bgB * 255.f);
        } else {
            c.A = 255;
            if (opts.bgA < 1.f) {
                c.R = c.G = c.B = 255;
            } else {
                c.R = static_cast<BYTE>(opts.bgR * 255.f);
                c.G = static_cast<BYTE>(opts.bgG * 255.f);
                c.B = static_cast<BYTE>(opts.bgB * 255.f);
            }
        }
        controller2->put_DefaultBackgroundColor(c);
    }

    if (opts.zoomLevel > 0.0) {
        s->controller->put_ZoomFactor(opts.zoomLevel);
    }

    hr = joinWindowTree(s);
    if (FAILED(hr)) {
        logCreateFailure("joining the window's composition tree", hr);
        delete s;
        return nullptr;
    }
    hr = s->compController->put_RootVisualTarget(s->rootVisual.Get());
    if (FAILED(hr)) {
        logCreateFailure("setting the view's root visual", hr);
        leaveWindowTree(s);
        delete s;
        return nullptr;
    }

    compose_webview_apply_bounds(*s);
    s->dcompDevice->Commit();

    /* window.ipc transport shim (WebView2 flavour). */
    const wchar_t *ipcShim =
        L"(function(){"
        L"  if (typeof window.ipc === 'undefined') {"
        L"    window.ipc = {"
        L"      postMessage: function(message) {"
        L"        try {"
        L"          if (window.chrome && window.chrome.webview) {"
        L"            window.chrome.webview.postMessage("
        L"              (typeof message === 'string') ? message : JSON.stringify(message)"
        L"            );"
        L"          }"
        L"        } catch (e) {}"
        L"      }"
        L"    };"
        L"  }"
        L"})();";
    s->webview->AddScriptToExecuteOnDocumentCreated(ipcShim, nullptr);

    /* JS bridge object, built once in Kotlin (honours a custom jsBridgeName).
     * Injected at document start so page scripts can call it without waiting
     * on a Compose Finished race after each navigation. */
    if (!opts.jsBridgeScript.empty()) {
        s->webview->AddScriptToExecuteOnDocumentCreated(
            opts.jsBridgeScript.c_str(), nullptr);
    }

    if (!opts.transparent) {
        s->webview->AddScriptToExecuteOnDocumentCreated(
            L"(function(){"
            L"var s=document.createElement('style');"
            L"s.textContent='html, body { background-color: #ffffff !important; }';"
            L"document.documentElement.appendChild(s);"
            L"})();",
            nullptr);
    }
    if (!opts.initScript.empty()) {
        s->webview->AddScriptToExecuteOnDocumentCreated(opts.initScript.c_str(), nullptr);
    }

    compose_webview_hook_events(s);
    compose_webview_install_parent_subclass(s);
    s->controller->put_IsVisible(TRUE);
    return s;
}

void compose_webview_release(ComposeWebViewState *s) {
    if (!s) return;
    compose_webview_uninstall_parent_subclass(s);
    compose_webview_unhook_events(s);
    if (s->controller) s->controller->Close();
    leaveWindowTree(s);
    delete s;
}

/* ── JNI: create / release / bounds ─────────────────────────────────────── */

extern "C" {

JNIEXPORT jlong JNICALL
Java_dev_nucleusframework_webview_web_windows_WebView2WindowsBridge_nativeCreate(
    JNIEnv *env,
    jclass,
    jlong parentHwnd,
    jstring userAgent,
    jstring dataDirectory,
    jstring initScript,
    jstring jsBridgeScript,
    jboolean incognito,
    jboolean enableDevtools,
    jboolean javascriptEnabled,
    jdouble zoomLevel,
    jboolean transparent,
    jfloat bgR,
    jfloat bgG,
    jfloat bgB,
    jfloat bgA) {
    if (parentHwnd == 0) return 0;
    HWND parent = reinterpret_cast<HWND>(static_cast<uintptr_t>(parentHwnd));
    if (!IsWindow(parent)) return 0;

    compose_webview_ensure_bridge_methods(env);

    ComposeWebViewCreateOptions opts;
    opts.userAgent = compose_webview_jstring_to_wide(env, userAgent);
    opts.dataDirectory = compose_webview_jstring_to_wide(env, dataDirectory);
    opts.initScript = compose_webview_jstring_to_wide(env, initScript);
    opts.jsBridgeScript = compose_webview_jstring_to_wide(env, jsBridgeScript);
    opts.incognito = incognito == JNI_TRUE;
    opts.enableDevtools = enableDevtools == JNI_TRUE;
    opts.javascriptEnabled = javascriptEnabled == JNI_TRUE;
    opts.zoomLevel = zoomLevel;
    opts.transparent = transparent == JNI_TRUE;
    opts.bgR = bgR;
    opts.bgG = bgG;
    opts.bgB = bgB;
    opts.bgA = bgA;

    ComposeWebViewState *s = compose_webview_create(parent, opts);
    if (!s) return 0;
    return compose_webview_register(s);
}

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_windows_WebView2WindowsBridge_nativeRelease(
    JNIEnv *, jclass, jlong handle) {
    compose_webview_release(compose_webview_unregister(handle));
}

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_windows_WebView2WindowsBridge_nativeSetBounds(
    JNIEnv *, jclass, jlong handle, jint xPx, jint yPx, jint widthPx, jint heightPx) {
    ComposeWebViewState *s = compose_webview_state_from_handle(handle);
    if (!s) return;
    if (widthPx < 1) widthPx = 1;
    if (heightPx < 1) heightPx = 1;
    s->xPx = xPx;
    s->yPx = yPx;
    s->widthPx = widthPx;
    s->heightPx = heightPx;
    compose_webview_apply_bounds(*s);
    compose_webview_apply_rounded_clip(*s);
    if (s->dcompDevice) s->dcompDevice->Commit();
    if (s->controller) s->controller->NotifyParentWindowPositionChanged();
}

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_windows_WebView2WindowsBridge_nativeSetCornerRadius(
    JNIEnv *, jclass, jlong handle, jfloat radiusPx) {
    ComposeWebViewState *s = compose_webview_state_from_handle(handle);
    if (!s) return;
    if (radiusPx < 0) radiusPx = 0;
    s->cornerRadiusPx = radiusPx;
    compose_webview_apply_rounded_clip(*s);
    if (s->dcompDevice) s->dcompDevice->Commit();
}

}  /* extern "C" */

BOOL APIENTRY DllMain(HMODULE, DWORD /*reason*/, LPVOID) {
    return TRUE;
}
