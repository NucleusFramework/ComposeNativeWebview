/**
 * Shared state and helpers for the Windows WebView2 backend.
 * Not a public API — only used by the compose_webview_*.cpp units.
 *
 * Pattern: CoreWebView2CompositionController + DirectComposition
 * (see Nucleus tao-demo sample_webview.cpp). SetWindowRgn does not work
 * because WebView2 paints via DComp.
 */
#ifndef COMPOSE_WEBVIEW_INTERNAL_H
#define COMPOSE_WEBVIEW_INTERNAL_H

#include <jni.h>
#include <windows.h>
#include <windowsx.h>
#include <unknwn.h>
#include <wrl.h>
#include <dcomp.h>
#include <WebView2.h>

#include <atomic>
#include <mutex>
#include <string>
#include <vector>

using Microsoft::WRL::ComPtr;

struct ComposeWebViewState {
    jlong handle = 0;
    HWND parent = nullptr;
    WNDPROC originalParentProc = nullptr;
    bool subclassInstalled = false;

    ComPtr<ICoreWebView2Environment> env;
    ComPtr<ICoreWebView2CompositionController> compController;
    ComPtr<ICoreWebView2Controller> controller;
    ComPtr<ICoreWebView2> webview;
    ComPtr<ICoreWebView2_2> webview2;
    ComPtr<ICoreWebView2CookieManager> cookieManager;

    ComPtr<IDCompositionDevice> dcompDevice;
    ComPtr<IDCompositionTarget> dcompTarget;
    ComPtr<IDCompositionVisual> rootVisual;

    int xPx = 0;
    int yPx = 0;
    int widthPx = 800;
    int heightPx = 600;
    float cornerRadiusPx = 0.0f;

    std::atomic<bool> isLoading{false};
    std::atomic<bool> canGoBack{false};
    std::atomic<bool> canGoForward{false};
    std::wstring lastSource;
    std::wstring lastTitle;
    std::mutex sourceMutex;

    std::atomic<HCURSOR> currentCursor{nullptr};

    EventRegistrationToken navigationStartingToken{};
    EventRegistrationToken navigationCompletedToken{};
    EventRegistrationToken sourceChangedToken{};
    EventRegistrationToken historyChangedToken{};
    EventRegistrationToken documentTitleChangedToken{};
    EventRegistrationToken cursorChangedToken{};
    EventRegistrationToken webMessageToken{};
};

struct ComposeWebViewCreateOptions {
    std::wstring userAgent;
    std::wstring dataDirectory;
    std::wstring initScript;
    bool incognito = false;
    bool enableDevtools = false;
    bool javascriptEnabled = true;
    double zoomLevel = 1.0;
    bool transparent = false;
    float bgR = 1.f;
    float bgG = 1.f;
    float bgB = 1.f;
    float bgA = 1.f;
};

/* jni_bridge.cpp */
JNIEnv *compose_webview_get_env(void);
void compose_webview_ensure_bridge_methods(JNIEnv *env);
void compose_webview_call_on_js_result(jlong handle, const std::string &utf8);
void compose_webview_call_on_ipc(jlong handle, const std::string &utf8);
bool compose_webview_call_on_navigate(jlong handle, const std::wstring &url);
void compose_webview_call_on_cookies(jlong handle, const std::string &json);
void compose_webview_call_on_screenshot(jlong handle, const std::vector<BYTE> *png);

/* helpers (view_lifecycle.cpp) */
std::wstring compose_webview_jstring_to_wide(JNIEnv *env, jstring s);
jstring compose_webview_wide_to_jstring(JNIEnv *env, const std::wstring &s);
std::string compose_webview_wide_to_utf8(const std::wstring &w);
std::wstring compose_webview_utf8_to_wide(const std::string &u);
std::string compose_webview_json_escape(const std::string &s);

ComposeWebViewState *compose_webview_state_from_handle(jlong handle);
void compose_webview_pump_until_done(const std::atomic<bool> &done);
void compose_webview_apply_bounds(ComposeWebViewState &s);
void compose_webview_apply_rounded_clip(ComposeWebViewState &s);

/* view_input.cpp — parent HWND subclass for SendMouseInput */
void compose_webview_install_parent_subclass(ComposeWebViewState *s);
void compose_webview_uninstall_parent_subclass(ComposeWebViewState *s);
bool compose_webview_inside(ComposeWebViewState *s, int xClient, int yClient);

/* view_signals.cpp — wired during create */
void compose_webview_hook_events(ComposeWebViewState *s);
void compose_webview_unhook_events(ComposeWebViewState *s);

/* view_lifecycle.cpp */
ComposeWebViewState *compose_webview_create(HWND parent, const ComposeWebViewCreateOptions &opts);
void compose_webview_release(ComposeWebViewState *s);
jlong compose_webview_register(ComposeWebViewState *s);
ComposeWebViewState *compose_webview_unregister(jlong handle);

#endif /* COMPOSE_WEBVIEW_INTERNAL_H */
