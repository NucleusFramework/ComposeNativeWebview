#include "compose_webview_internal.h"

typedef struct {
    jlong handle;
} SnapshotData;

static cairo_status_t png_write_to_gbyte_array(
    void *closure,
    const unsigned char *data,
    unsigned int length)
{
    GByteArray *array = (GByteArray *) closure;
    g_byte_array_append(array, data, length);
    return CAIRO_STATUS_SUCCESS;
}

static void on_snapshot_finished(
    GObject *source,
    GAsyncResult *result,
    gpointer user_data)
{
    SnapshotData *data = (SnapshotData *) user_data;
    WebKitWebView *view = WEBKIT_WEB_VIEW(source);
    GError *error = NULL;
    cairo_surface_t *surface =
        webkit_web_view_get_snapshot_finish(view, result, &error);

    JNIEnv *env = compose_webview_get_env();
    jbyteArray jbytes = NULL;

    if (error == NULL && surface != NULL && env != NULL) {
        GByteArray *png = g_byte_array_new();
        cairo_status_t st = cairo_surface_write_to_png_stream(
            surface, png_write_to_gbyte_array, png);
        if (st == CAIRO_STATUS_SUCCESS && png->len > 0) {
            jbytes = (*env)->NewByteArray(env, (jsize) png->len);
            if (jbytes != NULL) {
                (*env)->SetByteArrayRegion(
                    env, jbytes, 0, (jsize) png->len, (const jbyte *) png->data);
            }
        }
        g_byte_array_free(png, TRUE);
        cairo_surface_destroy(surface);
    }
    if (error != NULL) g_error_free(error);

    if (env != NULL) {
        compose_webview_ensure_bridge_methods(env);
        if (compose_webview_bridge_class() != NULL && compose_webview_on_screenshot() != NULL) {
            (*env)->CallStaticVoidMethod(
                env, compose_webview_bridge_class(), compose_webview_on_screenshot(), data->handle, jbytes);
            if ((*env)->ExceptionCheck(env)) {
                (*env)->ExceptionClear(env);
            }
        }
        if (jbytes != NULL) (*env)->DeleteLocalRef(env, jbytes);
    }
    g_free(data);
}

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_linux_WebKitLinuxBridge_nativeCaptureScreenshot(
    JNIEnv *env, jclass clazz, jlong handle)
{
    (void) env; (void) clazz;
    ComposeWebViewState *state = compose_webview_state_from_handle(handle);
    if (state == NULL || state->web_view == NULL) {
        JNIEnv *jenv = compose_webview_get_env();
        if (jenv != NULL) {
            compose_webview_ensure_bridge_methods(jenv);
            if (compose_webview_bridge_class() != NULL && compose_webview_on_screenshot() != NULL) {
                (*jenv)->CallStaticVoidMethod(
                    jenv, compose_webview_bridge_class(), compose_webview_on_screenshot(), handle, NULL);
            }
        }
        return;
    }
    SnapshotData *data = g_new0(SnapshotData, 1);
    data->handle = handle;
    webkit_web_view_get_snapshot(
        state->web_view,
        WEBKIT_SNAPSHOT_REGION_VISIBLE,
        WEBKIT_SNAPSHOT_OPTIONS_NONE,
        NULL,
        on_snapshot_finished,
        data);
}
