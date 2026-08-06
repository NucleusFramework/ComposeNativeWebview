#include "compose_webview_internal.h"

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_macos_WebKitMacOsBridge_nativeLoadUrl(
    JNIEnv *env, jclass clazz, jlong handle, jstring url_str)
{
    (void)clazz;
    ComposeWebViewState *state = compose_webview_state_from_handle(handle);
    if (state == nil || state.webView == nil || url_str == NULL) return;
    NSString *url = compose_webview_jstring_to_ns(env, url_str);
    if (url == nil) return;
    NSURL *nsurl = [NSURL URLWithString:url];
    if (nsurl == nil) return;
    [state.webView loadRequest:[NSURLRequest requestWithURL:nsurl]];
}

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_macos_WebKitMacOsBridge_nativeLoadUrlWithHeaders(
    JNIEnv *env,
    jclass clazz,
    jlong handle,
    jstring url_str,
    jobjectArray header_names,
    jobjectArray header_values)
{
    (void)clazz;
    ComposeWebViewState *state = compose_webview_state_from_handle(handle);
    if (state == nil || state.webView == nil || url_str == NULL) return;

    NSString *url = compose_webview_jstring_to_ns(env, url_str);
    if (url == nil) return;
    NSURL *nsurl = [NSURL URLWithString:url];
    if (nsurl == nil) return;

    NSMutableURLRequest *request = [NSMutableURLRequest requestWithURL:nsurl];
    if (header_names != NULL && header_values != NULL) {
        jsize count = (*env)->GetArrayLength(env, header_names);
        jsize value_count = (*env)->GetArrayLength(env, header_values);
        if (value_count < count) count = value_count;
        for (jsize i = 0; i < count; i++) {
            jstring jn = (jstring)(*env)->GetObjectArrayElement(env, header_names, i);
            jstring jv = (jstring)(*env)->GetObjectArrayElement(env, header_values, i);
            NSString *n = compose_webview_jstring_to_ns(env, jn);
            NSString *v = compose_webview_jstring_to_ns(env, jv);
            if (n != nil && v != nil) {
                [request setValue:v forHTTPHeaderField:n];
            }
            if (jn != NULL) (*env)->DeleteLocalRef(env, jn);
            if (jv != NULL) (*env)->DeleteLocalRef(env, jv);
        }
    }
    [state.webView loadRequest:request];
}

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_macos_WebKitMacOsBridge_nativeLoadHtml(
    JNIEnv *env, jclass clazz, jlong handle, jstring html_str, jstring base_uri)
{
    (void)clazz;
    ComposeWebViewState *state = compose_webview_state_from_handle(handle);
    if (state == nil || state.webView == nil || html_str == NULL) return;
    NSString *html = compose_webview_jstring_to_ns(env, html_str);
    if (html == nil) return;
    NSURL *base = nil;
    if (base_uri != NULL) {
        NSString *baseStr = compose_webview_jstring_to_ns(env, base_uri);
        if (baseStr != nil) base = [NSURL URLWithString:baseStr];
    }
    [state.webView loadHTMLString:html baseURL:base];
}

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_macos_WebKitMacOsBridge_nativeGoBack(
    JNIEnv *env, jclass clazz, jlong handle)
{
    (void)env; (void)clazz;
    ComposeWebViewState *state = compose_webview_state_from_handle(handle);
    if (state == nil || state.webView == nil) return;
    [state.webView goBack];
}

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_macos_WebKitMacOsBridge_nativeGoForward(
    JNIEnv *env, jclass clazz, jlong handle)
{
    (void)env; (void)clazz;
    ComposeWebViewState *state = compose_webview_state_from_handle(handle);
    if (state == nil || state.webView == nil) return;
    [state.webView goForward];
}

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_macos_WebKitMacOsBridge_nativeReload(
    JNIEnv *env, jclass clazz, jlong handle)
{
    (void)env; (void)clazz;
    ComposeWebViewState *state = compose_webview_state_from_handle(handle);
    if (state == nil || state.webView == nil) return;
    [state.webView reload];
}

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_macos_WebKitMacOsBridge_nativeStopLoading(
    JNIEnv *env, jclass clazz, jlong handle)
{
    (void)env; (void)clazz;
    ComposeWebViewState *state = compose_webview_state_from_handle(handle);
    if (state == nil || state.webView == nil) return;
    [state.webView stopLoading];
}

JNIEXPORT jboolean JNICALL
Java_dev_nucleusframework_webview_web_macos_WebKitMacOsBridge_nativeCanGoBack(
    JNIEnv *env, jclass clazz, jlong handle)
{
    (void)env; (void)clazz;
    ComposeWebViewState *state = compose_webview_state_from_handle(handle);
    if (state == nil || state.webView == nil) return JNI_FALSE;
    return state.webView.canGoBack ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_dev_nucleusframework_webview_web_macos_WebKitMacOsBridge_nativeCanGoForward(
    JNIEnv *env, jclass clazz, jlong handle)
{
    (void)env; (void)clazz;
    ComposeWebViewState *state = compose_webview_state_from_handle(handle);
    if (state == nil || state.webView == nil) return JNI_FALSE;
    return state.webView.canGoForward ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_dev_nucleusframework_webview_web_macos_WebKitMacOsBridge_nativeCurrentUrl(
    JNIEnv *env, jclass clazz, jlong handle)
{
    (void)clazz;
    ComposeWebViewState *state = compose_webview_state_from_handle(handle);
    if (state == nil || state.webView == nil) return NULL;
    return compose_webview_ns_to_jstring(env, state.webView.URL.absoluteString);
}

JNIEXPORT jstring JNICALL
Java_dev_nucleusframework_webview_web_macos_WebKitMacOsBridge_nativeGetTitle(
    JNIEnv *env, jclass clazz, jlong handle)
{
    (void)clazz;
    ComposeWebViewState *state = compose_webview_state_from_handle(handle);
    if (state == nil || state.webView == nil) return NULL;
    return compose_webview_ns_to_jstring(env, state.webView.title);
}

JNIEXPORT jboolean JNICALL
Java_dev_nucleusframework_webview_web_macos_WebKitMacOsBridge_nativeIsLoading(
    JNIEnv *env, jclass clazz, jlong handle)
{
    (void)env; (void)clazz;
    ComposeWebViewState *state = compose_webview_state_from_handle(handle);
    if (state == nil || state.webView == nil) return JNI_FALSE;
    return state.webView.loading ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_macos_WebKitMacOsBridge_nativeSetZoomLevel(
    JNIEnv *env, jclass clazz, jlong handle, jdouble zoom)
{
    (void)env; (void)clazz;
    ComposeWebViewState *state = compose_webview_state_from_handle(handle);
    if (state == nil || state.webView == nil) return;
    state.webView.pageZoom = zoom;
}

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_macos_WebKitMacOsBridge_nativeFocus(
    JNIEnv *env, jclass clazz, jlong handle)
{
    (void)env; (void)clazz;
    ComposeWebViewState *state = compose_webview_state_from_handle(handle);
    if (state == nil || state.webView == nil) return;
    NSWindow *window = state.webView.window;
    if (window != nil) {
        [window makeFirstResponder:state.webView];
    }
}

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_macos_WebKitMacOsBridge_nativeOpenDevTools(
    JNIEnv *env, jclass clazz, jlong handle)
{
    (void)env; (void)clazz;
    ComposeWebViewState *state = compose_webview_state_from_handle(handle);
    if (state == nil || state.webView == nil) return;
    if (@available(macOS 13.3, *)) {
        state.webView.inspectable = YES;
    }
}

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_macos_WebKitMacOsBridge_nativeCloseDevTools(
    JNIEnv *env, jclass clazz, jlong handle)
{
    (void)env; (void)clazz; (void)handle;
}
