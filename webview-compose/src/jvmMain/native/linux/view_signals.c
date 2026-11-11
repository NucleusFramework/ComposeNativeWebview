#include "compose_webview_internal.h"

gboolean compose_webview_on_decide_policy(
    WebKitWebView *web_view,
    WebKitPolicyDecision *decision,
    WebKitPolicyDecisionType type,
    gpointer user_data)
{
    (void) user_data;
    if (type != WEBKIT_POLICY_DECISION_TYPE_NAVIGATION_ACTION &&
        type != WEBKIT_POLICY_DECISION_TYPE_NEW_WINDOW_ACTION) {
        return FALSE;
    }

    WebKitNavigationPolicyDecision *nav =
        WEBKIT_NAVIGATION_POLICY_DECISION(decision);
    WebKitNavigationAction *action =
        webkit_navigation_policy_decision_get_navigation_action(nav);
    WebKitURIRequest *request = webkit_navigation_action_get_request(action);
    const gchar *uri = webkit_uri_request_get_uri(request);
    if (uri == NULL) {
        webkit_policy_decision_use(decision);
        return TRUE;
    }

    if (g_str_has_prefix(uri, "about:") ||
        g_str_has_prefix(uri, "data:") ||
        g_str_has_prefix(uri, "blob:")) {
        webkit_policy_decision_use(decision);
        return TRUE;
    }

    JNIEnv *env = compose_webview_get_env();
    if (env == NULL) {
        webkit_policy_decision_use(decision);
        return TRUE;
    }
    compose_webview_ensure_bridge_methods(env);
    if (compose_webview_bridge_class() == NULL || compose_webview_on_navigate() == NULL) {
        webkit_policy_decision_use(decision);
        return TRUE;
    }

    jlong handle = compose_webview_handle_from_view(web_view);
    jstring juri = (*env)->NewStringUTF(env, uri);
    jboolean allow = (*env)->CallStaticBooleanMethod(
        env, compose_webview_bridge_class(), compose_webview_on_navigate(), handle, juri);
    (*env)->DeleteLocalRef(env, juri);

    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        webkit_policy_decision_use(decision);
        return TRUE;
    }

    if (allow) {
        webkit_policy_decision_use(decision);
    } else {
        webkit_policy_decision_ignore(decision);
    }
    return TRUE;
}

/*
 * Hands a page message to Kotlin with what is known about its sender. `origin` is NULL when
 * nothing vouches for the sender, and `is_main_frame` says nothing then.
 */
static void compose_webview_deliver_ipc(
    ComposeWebViewState *state,
    const gchar *message,
    const gchar *origin,
    gboolean is_main_frame)
{
    JNIEnv *env = compose_webview_get_env();
    if (env != NULL) {
        compose_webview_ensure_bridge_methods(env);
        if (compose_webview_bridge_class() != NULL && compose_webview_on_ipc() != NULL) {
            jlong handle = (jlong) (uintptr_t) state;
            jstring jmsg = (*env)->NewStringUTF(env, message);
            jstring jorigin = origin != NULL ? (*env)->NewStringUTF(env, origin) : NULL;
            (*env)->CallStaticVoidMethod(
                env, compose_webview_bridge_class(), compose_webview_on_ipc(), handle, jmsg,
                jorigin, (jboolean) (is_main_frame ? JNI_TRUE : JNI_FALSE));
            (*env)->DeleteLocalRef(env, jmsg);
            if (jorigin != NULL) (*env)->DeleteLocalRef(env, jorigin);
            if ((*env)->ExceptionCheck(env)) {
                (*env)->ExceptionClear(env);
            }
        }
    }
}

/*
 * A message posted straight to the page-visible handler. WebKitGTK says nothing about who
 * posted it, so it carries no sender.
 */
void compose_webview_on_script_message(
    WebKitUserContentManager *manager,
    WebKitJavascriptResult *js_result,
    gpointer user_data)
{
    (void) manager;
    ComposeWebViewState *state = (ComposeWebViewState *) user_data;
    if (state == NULL || state->web_view == NULL || js_result == NULL) return;

    /* WebKitGTK 4.1 still delivers WebKitJavascriptResult on this signal
     * (not a bare JSCValue*). Extract the JSCValue first. */
    JSCValue *value = webkit_javascript_result_get_js_value(js_result);
    if (value == NULL || !JSC_IS_VALUE(value)) return;

    gchar *message = NULL;
    if (jsc_value_is_string(value)) {
        message = jsc_value_to_string(value);
    } else {
        message = jsc_value_to_json(value, 0);
    }
    if (message == NULL) return;

    compose_webview_deliver_ipc(state, message, NULL, FALSE);
    g_free(message);
}

/*
 * A page message forwarded by the relay script (view_lifecycle.c). Only that script can reach
 * the handler this arrives on, so the origin and frame it names are the ones WebKit gave the
 * sending document, not ones the page chose.
 */
void compose_webview_on_relayed_script_message(
    WebKitUserContentManager *manager,
    WebKitJavascriptResult *js_result,
    gpointer user_data)
{
    (void) manager;
    ComposeWebViewState *state = (ComposeWebViewState *) user_data;
    if (state == NULL || state->web_view == NULL || js_result == NULL) return;

    JSCValue *value = webkit_javascript_result_get_js_value(js_result);
    if (value == NULL || !JSC_IS_VALUE(value) || !jsc_value_is_object(value)) return;

    JSCValue *origin = jsc_value_object_get_property(value, "origin");
    JSCValue *is_main_frame = jsc_value_object_get_property(value, "isMainFrame");
    JSCValue *body = jsc_value_object_get_property(value, "body");
    if (jsc_value_is_string(origin) && jsc_value_is_boolean(is_main_frame) &&
        jsc_value_is_string(body)) {
        gchar *origin_text = jsc_value_to_string(origin);
        gchar *body_text = jsc_value_to_string(body);
        compose_webview_deliver_ipc(
            state, body_text, origin_text, jsc_value_to_boolean(is_main_frame));
        g_free(origin_text);
        g_free(body_text);
    }
    g_object_unref(origin);
    g_object_unref(is_main_frame);
    g_object_unref(body);
}
