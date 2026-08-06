#include "compose_webview_internal.h"

typedef struct {
    jlong handle;
} JsEvalData;

static void on_js_finished(
    GObject *source,
    GAsyncResult *result,
    gpointer user_data)
{
    JsEvalData *data = (JsEvalData *) user_data;
    WebKitWebView *view = WEBKIT_WEB_VIEW(source);
    GError *error = NULL;
    JSCValue *value = webkit_web_view_evaluate_javascript_finish(view, result, &error);

    gchar *payload = NULL;
    if (error != NULL) {
        payload = g_strdup("");
        g_error_free(error);
    } else if (value == NULL) {
        payload = g_strdup("");
    } else if (jsc_value_is_undefined(value) || jsc_value_is_null(value)) {
        payload = g_strdup("null");
        g_object_unref(value);
    } else if (jsc_value_is_string(value)) {
        /* Match Android evaluateJavascript: JSON-encoded string with quotes. */
        gchar *raw = jsc_value_to_string(value);
        gchar *escaped = g_strescape(raw, NULL);
        payload = g_strdup_printf("\"%s\"", escaped ? escaped : "");
        g_free(escaped);
        g_free(raw);
        g_object_unref(value);
    } else {
        payload = jsc_value_to_json(value, 0);
        if (payload == NULL) payload = g_strdup("");
        g_object_unref(value);
    }

    JNIEnv *env = compose_webview_get_env();
    if (env != NULL) {
        compose_webview_ensure_bridge_methods(env);
        if (compose_webview_bridge_class() != NULL && compose_webview_on_js_result() != NULL) {
            jstring jpayload = (*env)->NewStringUTF(env, payload ? payload : "");
            (*env)->CallStaticVoidMethod(
                env, compose_webview_bridge_class(), compose_webview_on_js_result(), data->handle, jpayload);
            (*env)->DeleteLocalRef(env, jpayload);
            if ((*env)->ExceptionCheck(env)) {
                (*env)->ExceptionClear(env);
            }
        }
    }
    g_free(payload);
    g_free(data);
}

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_linux_WebKitLinuxBridge_nativeEvaluateJavaScript(
    JNIEnv *env, jclass clazz, jlong handle, jstring script_str)
{
    (void) clazz;
    ComposeWebViewState *state = compose_webview_state_from_handle(handle);
    if (state == NULL || state->web_view == NULL || script_str == NULL) {
        if (script_str != NULL) {
            /* still need to complete the callback */
        }
        compose_webview_ensure_bridge_methods(env);
        if (compose_webview_bridge_class() != NULL && compose_webview_on_js_result() != NULL) {
            jstring empty = (*env)->NewStringUTF(env, "");
            (*env)->CallStaticVoidMethod(
                env, compose_webview_bridge_class(), compose_webview_on_js_result(), handle, empty);
            (*env)->DeleteLocalRef(env, empty);
        }
        return;
    }

    const char *script = (*env)->GetStringUTFChars(env, script_str, NULL);
    if (script == NULL) return;

    JsEvalData *data = g_new0(JsEvalData, 1);
    data->handle = handle;
    webkit_web_view_evaluate_javascript(
        state->web_view,
        script,
        -1,
        NULL,
        NULL,
        NULL,
        on_js_finished,
        data);
    (*env)->ReleaseStringUTFChars(env, script_str, script);
}

