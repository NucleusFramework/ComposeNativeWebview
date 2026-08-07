#include "compose_webview_internal.h"

using Microsoft::WRL::Callback;

extern "C" {

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_windows_WebView2WindowsBridge_nativeEvaluateJavaScript(
    JNIEnv *env, jclass, jlong handle, jstring script) {
    ComposeWebViewState *s = compose_webview_state_from_handle(handle);
    if (!s || !s->webview || !script) {
        compose_webview_call_on_js_result(handle, "");
        return;
    }
    std::wstring w = compose_webview_jstring_to_wide(env, script);
    jlong h = handle;
    s->webview->ExecuteScript(
        w.c_str(),
        Callback<ICoreWebView2ExecuteScriptCompletedHandler>(
            [h](HRESULT result, LPCWSTR resultJson) -> HRESULT {
                if (FAILED(result) || !resultJson) {
                    compose_webview_call_on_js_result(h, "");
                } else {
                    compose_webview_call_on_js_result(
                        h, compose_webview_wide_to_utf8(resultJson));
                }
                return S_OK;
            }).Get());
}

}  /* extern "C" */
