#include "compose_webview_internal.h"

/**
 * Percent-encode [input] for a data: URL body. Keeps alphanumerics and
 * a small unreserved set so history entries are distinct and GoBack works
 * (NavigateToString often does not push usable history on WebView2).
 */
static std::wstring percentEncodeUtf8(const std::string &input) {
    static const char *hex = "0123456789ABCDEF";
    std::wstring out;
    out.reserve(input.size() * 3);
    for (unsigned char c : input) {
        if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') ||
            (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.' || c == '~') {
            out.push_back(static_cast<wchar_t>(c));
        } else {
            out.push_back(L'%');
            out.push_back(static_cast<wchar_t>(hex[c >> 4]));
            out.push_back(static_cast<wchar_t>(hex[c & 0xF]));
        }
    }
    return out;
}

extern "C" {

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_windows_WebView2WindowsBridge_nativeLoadUrl(
    JNIEnv *env, jclass, jlong handle, jstring url) {
    ComposeWebViewState *s = compose_webview_state_from_handle(handle);
    if (!s || !s->webview || !url) return;
    s->webview->Navigate(compose_webview_jstring_to_wide(env, url).c_str());
}

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_windows_WebView2WindowsBridge_nativeLoadUrlWithHeaders(
    JNIEnv *env,
    jclass,
    jlong handle,
    jstring url,
    jobjectArray headerNames,
    jobjectArray headerValues) {
    ComposeWebViewState *s = compose_webview_state_from_handle(handle);
    if (!s || !s->webview || !url) return;

    if (s->env && headerNames && headerValues) {
        ComPtr<ICoreWebView2Environment2> env2;
        if (SUCCEEDED(s->env.As(&env2)) && env2) {
            std::wstring wurl = compose_webview_jstring_to_wide(env, url);
            ComPtr<ICoreWebView2WebResourceRequest> request;
            if (SUCCEEDED(env2->CreateWebResourceRequest(
                    wurl.c_str(), L"GET", nullptr, L"", &request)) &&
                request) {
                ComPtr<ICoreWebView2HttpRequestHeaders> headers;
                if (SUCCEEDED(request->get_Headers(&headers)) && headers) {
                    jsize count = env->GetArrayLength(headerNames);
                    jsize vcount = env->GetArrayLength(headerValues);
                    if (vcount < count) count = vcount;
                    for (jsize i = 0; i < count; i++) {
                        auto jn = static_cast<jstring>(
                            env->GetObjectArrayElement(headerNames, i));
                        auto jv = static_cast<jstring>(
                            env->GetObjectArrayElement(headerValues, i));
                        if (jn && jv) {
                            headers->SetHeader(
                                compose_webview_jstring_to_wide(env, jn).c_str(),
                                compose_webview_jstring_to_wide(env, jv).c_str());
                        }
                        if (jn) env->DeleteLocalRef(jn);
                        if (jv) env->DeleteLocalRef(jv);
                    }
                }
                ComPtr<ICoreWebView2_2> wv2;
                if (SUCCEEDED(s->webview.As(&wv2)) && wv2) {
                    wv2->NavigateWithWebResourceRequest(request.Get());
                    return;
                }
            }
        }
    }
    s->webview->Navigate(compose_webview_jstring_to_wide(env, url).c_str());
}

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_windows_WebView2WindowsBridge_nativeLoadHtml(
    JNIEnv *env, jclass, jlong handle, jstring html, jstring /*baseUri*/) {
    ComposeWebViewState *s = compose_webview_state_from_handle(handle);
    if (!s || !s->webview || !html) return;
    /* data: URL (not NavigateToString) so back/forward history works. baseUri ignored. */
    std::wstring wide = compose_webview_jstring_to_wide(env, html);
    std::string utf8 = compose_webview_wide_to_utf8(wide);
    std::wstring url = L"data:text/html;charset=utf-8," + percentEncodeUtf8(utf8);
    s->webview->Navigate(url.c_str());
}

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_windows_WebView2WindowsBridge_nativeGoBack(
    JNIEnv *, jclass, jlong handle) {
    ComposeWebViewState *s = compose_webview_state_from_handle(handle);
    if (s && s->webview) s->webview->GoBack();
}

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_windows_WebView2WindowsBridge_nativeGoForward(
    JNIEnv *, jclass, jlong handle) {
    ComposeWebViewState *s = compose_webview_state_from_handle(handle);
    if (s && s->webview) s->webview->GoForward();
}

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_windows_WebView2WindowsBridge_nativeReload(
    JNIEnv *, jclass, jlong handle) {
    ComposeWebViewState *s = compose_webview_state_from_handle(handle);
    if (s && s->webview) s->webview->Reload();
}

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_windows_WebView2WindowsBridge_nativeStopLoading(
    JNIEnv *, jclass, jlong handle) {
    ComposeWebViewState *s = compose_webview_state_from_handle(handle);
    if (s && s->webview) s->webview->Stop();
}

JNIEXPORT jboolean JNICALL
Java_dev_nucleusframework_webview_web_windows_WebView2WindowsBridge_nativeCanGoBack(
    JNIEnv *, jclass, jlong handle) {
    ComposeWebViewState *s = compose_webview_state_from_handle(handle);
    return (s && s->canGoBack.load(std::memory_order_acquire)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_dev_nucleusframework_webview_web_windows_WebView2WindowsBridge_nativeCanGoForward(
    JNIEnv *, jclass, jlong handle) {
    ComposeWebViewState *s = compose_webview_state_from_handle(handle);
    return (s && s->canGoForward.load(std::memory_order_acquire)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_dev_nucleusframework_webview_web_windows_WebView2WindowsBridge_nativeCurrentUrl(
    JNIEnv *env, jclass, jlong handle) {
    ComposeWebViewState *s = compose_webview_state_from_handle(handle);
    if (!s) return nullptr;
    std::wstring copy;
    {
        std::lock_guard<std::mutex> lock(s->sourceMutex);
        copy = s->lastSource;
    }
    /* Live query as fallback — some data: navigations skip SourceChanged. */
    if (copy.empty() && s->webview) {
        LPWSTR src = nullptr;
        if (SUCCEEDED(s->webview->get_Source(&src)) && src) {
            copy.assign(src);
            {
                std::lock_guard<std::mutex> lock(s->sourceMutex);
                s->lastSource = copy;
            }
            CoTaskMemFree(src);
        }
    }
    if (copy.empty()) return nullptr;
    return compose_webview_wide_to_jstring(env, copy);
}

JNIEXPORT jstring JNICALL
Java_dev_nucleusframework_webview_web_windows_WebView2WindowsBridge_nativeGetTitle(
    JNIEnv *env, jclass, jlong handle) {
    ComposeWebViewState *s = compose_webview_state_from_handle(handle);
    if (!s) return nullptr;
    std::wstring copy;
    {
        std::lock_guard<std::mutex> lock(s->sourceMutex);
        copy = s->lastTitle;
    }
    if (copy.empty() && s->webview) {
        LPWSTR title = nullptr;
        if (SUCCEEDED(s->webview->get_DocumentTitle(&title)) && title) {
            copy.assign(title);
            {
                std::lock_guard<std::mutex> lock(s->sourceMutex);
                s->lastTitle = copy;
            }
            CoTaskMemFree(title);
        }
    }
    if (copy.empty()) return nullptr;
    return compose_webview_wide_to_jstring(env, copy);
}

JNIEXPORT jboolean JNICALL
Java_dev_nucleusframework_webview_web_windows_WebView2WindowsBridge_nativeIsLoading(
    JNIEnv *, jclass, jlong handle) {
    ComposeWebViewState *s = compose_webview_state_from_handle(handle);
    return (s && s->isLoading.load(std::memory_order_acquire)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_windows_WebView2WindowsBridge_nativeSetZoomLevel(
    JNIEnv *, jclass, jlong handle, jdouble zoom) {
    ComposeWebViewState *s = compose_webview_state_from_handle(handle);
    if (s && s->controller && zoom > 0.0) {
        s->controller->put_ZoomFactor(zoom);
    }
}

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_windows_WebView2WindowsBridge_nativeFocus(
    JNIEnv *, jclass, jlong handle) {
    ComposeWebViewState *s = compose_webview_state_from_handle(handle);
    if (s && s->controller) {
        s->controller->MoveFocus(COREWEBVIEW2_MOVE_FOCUS_REASON_PROGRAMMATIC);
    }
}

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_windows_WebView2WindowsBridge_nativeOpenDevTools(
    JNIEnv *, jclass, jlong handle) {
    ComposeWebViewState *s = compose_webview_state_from_handle(handle);
    if (s && s->webview) s->webview->OpenDevToolsWindow();
}

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_windows_WebView2WindowsBridge_nativeCloseDevTools(
    JNIEnv *, jclass, jlong /*handle*/) {
    /* WebView2 has no CloseDevTools API — no-op. */
}

}  /* extern "C" */
