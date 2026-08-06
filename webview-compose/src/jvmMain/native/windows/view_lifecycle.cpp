#include "compose_webview_internal.h"

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

void compose_webview_pump_until_done(const std::atomic<bool> &done) {
    while (!done.load(std::memory_order_acquire)) {
        MSG msg;
        if (GetMessageW(&msg, nullptr, 0, 0) <= 0) break;
        TranslateMessage(&msg);
        DispatchMessageW(&msg);
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
                if (SUCCEEDED(result) && env) s->env = env;
                envDone.store(true, std::memory_order_release);
                return S_OK;
            }).Get());
    if (FAILED(hr)) {
        delete s;
        return nullptr;
    }
    compose_webview_pump_until_done(envDone);
    if (FAILED(envResult) || !s->env) {
        delete s;
        return nullptr;
    }

    std::atomic<bool> ccDone{false};
    HRESULT ccResult = E_FAIL;
    ComPtr<ICoreWebView2Environment3> env3;
    if (FAILED(s->env.As(&env3)) || !env3) {
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
        delete s;
        return nullptr;
    }
    compose_webview_pump_until_done(ccDone);
    if (FAILED(ccResult) || !s->compController) {
        delete s;
        return nullptr;
    }

    if (FAILED(s->compController.As(&s->controller)) || !s->controller) {
        delete s;
        return nullptr;
    }
    if (FAILED(s->controller->get_CoreWebView2(&s->webview)) || !s->webview) {
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

    if (FAILED(DCompositionCreateDevice(nullptr, IID_PPV_ARGS(&s->dcompDevice))) ||
        FAILED(s->dcompDevice->CreateTargetForHwnd(parent, TRUE, &s->dcompTarget)) ||
        FAILED(s->dcompDevice->CreateVisual(&s->rootVisual))) {
        delete s;
        return nullptr;
    }
    s->dcompTarget->SetRoot(s->rootVisual.Get());
    if (FAILED(s->compController->put_RootVisualTarget(s->rootVisual.Get()))) {
        delete s;
        return nullptr;
    }

    compose_webview_apply_bounds(*s);
    s->dcompDevice->Commit();

    /* ipc shim + kmpJsBridge at document start so the suite does not depend
     * on a Compose Finished race to inject the bridge after each navigation. */
    const wchar_t *ipcAndBridgeShim =
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
        L"  if (typeof window.kmpJsBridge === 'undefined') {"
        L"    window.kmpJsBridge = {"
        L"      callbacks: {},"
        L"      callbackId: 0,"
        L"      callNative: function(methodName, params, callback) {"
        L"        var message = {"
        L"          methodName: methodName,"
        L"          params: params,"
        L"          callbackId: callback ? window.kmpJsBridge.callbackId++ : -1"
        L"        };"
        L"        if (callback) {"
        L"          window.kmpJsBridge.callbacks[message.callbackId] = callback;"
        L"        }"
        L"        window.kmpJsBridge.postMessage(JSON.stringify(message));"
        L"      },"
        L"      onCallback: function(callbackId, data) {"
        L"        var cb = window.kmpJsBridge.callbacks[callbackId];"
        L"        if (cb) { cb(data); delete window.kmpJsBridge.callbacks[callbackId]; }"
        L"      },"
        L"      postMessage: function(message) {"
        L"        if (window.ipc && window.ipc.postMessage) window.ipc.postMessage(message);"
        L"      }"
        L"    };"
        L"  }"
        L"})();";
    s->webview->AddScriptToExecuteOnDocumentCreated(ipcAndBridgeShim, nullptr);

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
