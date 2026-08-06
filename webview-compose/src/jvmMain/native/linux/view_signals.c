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

    JNIEnv *env = compose_webview_get_env();
    if (env != NULL) {
        compose_webview_ensure_bridge_methods(env);
        if (compose_webview_bridge_class() != NULL && compose_webview_on_ipc() != NULL) {
            jlong handle = (jlong) (uintptr_t) state;
            jstring jmsg = (*env)->NewStringUTF(env, message);
            (*env)->CallStaticVoidMethod(
                env, compose_webview_bridge_class(), compose_webview_on_ipc(), handle, jmsg);
            (*env)->DeleteLocalRef(env, jmsg);
            if ((*env)->ExceptionCheck(env)) {
                (*env)->ExceptionClear(env);
            }
        }
    }
    g_free(message);
}
