#include "compose_webview_internal.h"

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_linux_WebKitLinuxBridge_nativeLoadUrl(
    JNIEnv *env, jclass clazz, jlong handle, jstring url_str)
{
    (void) clazz;
    ComposeWebViewState *state = compose_webview_state_from_handle(handle);
    if (state == NULL || state->web_view == NULL || url_str == NULL) return;
    const char *cUrl = (*env)->GetStringUTFChars(env, url_str, NULL);
    if (cUrl == NULL) return;
    webkit_web_view_load_uri(state->web_view, cUrl);
    (*env)->ReleaseStringUTFChars(env, url_str, cUrl);
}

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_linux_WebKitLinuxBridge_nativeLoadUrlWithHeaders(
    JNIEnv *env,
    jclass clazz,
    jlong handle,
    jstring url_str,
    jobjectArray header_names,
    jobjectArray header_values)
{
    (void) clazz;
    ComposeWebViewState *state = compose_webview_state_from_handle(handle);
    if (state == NULL || state->web_view == NULL || url_str == NULL) return;

    const char *cUrl = (*env)->GetStringUTFChars(env, url_str, NULL);
    if (cUrl == NULL) return;

    WebKitURIRequest *request = webkit_uri_request_new(cUrl);
    SoupMessageHeaders *headers = webkit_uri_request_get_http_headers(request);
    if (headers != NULL && header_names != NULL && header_values != NULL) {
        jsize count = (*env)->GetArrayLength(env, header_names);
        jsize value_count = (*env)->GetArrayLength(env, header_values);
        if (value_count < count) count = value_count;
        for (jsize i = 0; i < count; i++) {
            jstring jn = (jstring) (*env)->GetObjectArrayElement(env, header_names, i);
            jstring jv = (jstring) (*env)->GetObjectArrayElement(env, header_values, i);
            if (jn != NULL && jv != NULL) {
                const char *n = (*env)->GetStringUTFChars(env, jn, NULL);
                const char *v = (*env)->GetStringUTFChars(env, jv, NULL);
                if (n != NULL && v != NULL) {
                    soup_message_headers_append(headers, n, v);
                }
                if (n != NULL) (*env)->ReleaseStringUTFChars(env, jn, n);
                if (v != NULL) (*env)->ReleaseStringUTFChars(env, jv, v);
            }
            if (jn != NULL) (*env)->DeleteLocalRef(env, jn);
            if (jv != NULL) (*env)->DeleteLocalRef(env, jv);
        }
    }
    webkit_web_view_load_request(state->web_view, request);
    g_object_unref(request);
    (*env)->ReleaseStringUTFChars(env, url_str, cUrl);
}

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_linux_WebKitLinuxBridge_nativeLoadHtml(
    JNIEnv *env, jclass clazz, jlong handle, jstring html_str, jstring base_uri)
{
    (void) clazz;
    ComposeWebViewState *state = compose_webview_state_from_handle(handle);
    if (state == NULL || state->web_view == NULL || html_str == NULL) return;
    const char *html = (*env)->GetStringUTFChars(env, html_str, NULL);
    if (html == NULL) return;
    const char *base = NULL;
    if (base_uri != NULL) {
        base = (*env)->GetStringUTFChars(env, base_uri, NULL);
    }
    webkit_web_view_load_html(state->web_view, html, base);
    (*env)->ReleaseStringUTFChars(env, html_str, html);
    if (base != NULL) {
        (*env)->ReleaseStringUTFChars(env, base_uri, base);
    }
}

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_linux_WebKitLinuxBridge_nativeGoBack(
    JNIEnv *env, jclass clazz, jlong handle)
{
    (void) env; (void) clazz;
    ComposeWebViewState *state = compose_webview_state_from_handle(handle);
    if (state == NULL || state->web_view == NULL) return;
    webkit_web_view_go_back(state->web_view);
}

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_linux_WebKitLinuxBridge_nativeGoForward(
    JNIEnv *env, jclass clazz, jlong handle)
{
    (void) env; (void) clazz;
    ComposeWebViewState *state = compose_webview_state_from_handle(handle);
    if (state == NULL || state->web_view == NULL) return;
    webkit_web_view_go_forward(state->web_view);
}

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_linux_WebKitLinuxBridge_nativeReload(
    JNIEnv *env, jclass clazz, jlong handle)
{
    (void) env; (void) clazz;
    ComposeWebViewState *state = compose_webview_state_from_handle(handle);
    if (state == NULL || state->web_view == NULL) return;
    webkit_web_view_reload(state->web_view);
}

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_linux_WebKitLinuxBridge_nativeStopLoading(
    JNIEnv *env, jclass clazz, jlong handle)
{
    (void) env; (void) clazz;
    ComposeWebViewState *state = compose_webview_state_from_handle(handle);
    if (state == NULL || state->web_view == NULL) return;
    webkit_web_view_stop_loading(state->web_view);
}

JNIEXPORT jboolean JNICALL
Java_dev_nucleusframework_webview_web_linux_WebKitLinuxBridge_nativeCanGoBack(
    JNIEnv *env, jclass clazz, jlong handle)
{
    (void) env; (void) clazz;
    ComposeWebViewState *state = compose_webview_state_from_handle(handle);
    if (state == NULL || state->web_view == NULL) return JNI_FALSE;
    return webkit_web_view_can_go_back(state->web_view) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_dev_nucleusframework_webview_web_linux_WebKitLinuxBridge_nativeCanGoForward(
    JNIEnv *env, jclass clazz, jlong handle)
{
    (void) env; (void) clazz;
    ComposeWebViewState *state = compose_webview_state_from_handle(handle);
    if (state == NULL || state->web_view == NULL) return JNI_FALSE;
    return webkit_web_view_can_go_forward(state->web_view) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_dev_nucleusframework_webview_web_linux_WebKitLinuxBridge_nativeCurrentUrl(
    JNIEnv *env, jclass clazz, jlong handle)
{
    (void) clazz;
    ComposeWebViewState *state = compose_webview_state_from_handle(handle);
    if (state == NULL || state->web_view == NULL) return NULL;
    const gchar *uri = webkit_web_view_get_uri(state->web_view);
    if (uri == NULL) return NULL;
    return (*env)->NewStringUTF(env, uri);
}

JNIEXPORT jstring JNICALL
Java_dev_nucleusframework_webview_web_linux_WebKitLinuxBridge_nativeGetTitle(
    JNIEnv *env, jclass clazz, jlong handle)
{
    (void) clazz;
    ComposeWebViewState *state = compose_webview_state_from_handle(handle);
    if (state == NULL || state->web_view == NULL) return NULL;
    const gchar *title = webkit_web_view_get_title(state->web_view);
    if (title == NULL) return NULL;
    return (*env)->NewStringUTF(env, title);
}

JNIEXPORT jboolean JNICALL
Java_dev_nucleusframework_webview_web_linux_WebKitLinuxBridge_nativeIsLoading(
    JNIEnv *env, jclass clazz, jlong handle)
{
    (void) env; (void) clazz;
    ComposeWebViewState *state = compose_webview_state_from_handle(handle);
    if (state == NULL || state->web_view == NULL) return JNI_FALSE;
    return webkit_web_view_is_loading(state->web_view) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_linux_WebKitLinuxBridge_nativeSetZoomLevel(
    JNIEnv *env, jclass clazz, jlong handle, jdouble zoom)
{
    (void) env; (void) clazz;
    ComposeWebViewState *state = compose_webview_state_from_handle(handle);
    if (state == NULL || state->web_view == NULL) return;
    webkit_web_view_set_zoom_level(state->web_view, zoom);
}

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_linux_WebKitLinuxBridge_nativeFocus(
    JNIEnv *env, jclass clazz, jlong handle)
{
    (void) env; (void) clazz;
    ComposeWebViewState *state = compose_webview_state_from_handle(handle);
    if (state == NULL || state->web_view == NULL) return;
    gtk_widget_grab_focus(GTK_WIDGET(state->web_view));
}

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_linux_WebKitLinuxBridge_nativeOpenDevTools(
    JNIEnv *env, jclass clazz, jlong handle)
{
    (void) env; (void) clazz;
    ComposeWebViewState *state = compose_webview_state_from_handle(handle);
    if (state == NULL || state->web_view == NULL) return;
    WebKitWebInspector *inspector = webkit_web_view_get_inspector(state->web_view);
    if (inspector != NULL) {
        webkit_web_inspector_show(inspector);
    }
}

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_linux_WebKitLinuxBridge_nativeCloseDevTools(
    JNIEnv *env, jclass clazz, jlong handle)
{
    (void) env; (void) clazz;
    ComposeWebViewState *state = compose_webview_state_from_handle(handle);
    if (state == NULL || state->web_view == NULL) return;
    WebKitWebInspector *inspector = webkit_web_view_get_inspector(state->web_view);
    if (inspector != NULL) {
        webkit_web_inspector_close(inspector);
    }
}

