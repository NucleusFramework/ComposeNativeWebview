#include "compose_webview_internal.h"

static WebKitCookieManager *cookie_manager_for(ComposeWebViewState *state) {
    if (state == NULL || state->web_view == NULL) return NULL;
    WebKitWebsiteDataManager *dm =
        webkit_web_view_get_website_data_manager(state->web_view);
    if (dm == NULL) return NULL;
    return webkit_website_data_manager_get_cookie_manager(dm);
}

static gchar *cookie_to_json(SoupCookie *cookie) {
    if (cookie == NULL) return g_strdup("null");
    const char *name = soup_cookie_get_name(cookie);
    const char *value = soup_cookie_get_value(cookie);
    const char *domain = soup_cookie_get_domain(cookie);
    const char *path = soup_cookie_get_path(cookie);
    gboolean secure = soup_cookie_get_secure(cookie);
    gboolean http_only = soup_cookie_get_http_only(cookie);
    GDateTime *expires = soup_cookie_get_expires(cookie);
    gint64 expires_ms = 0;
    gboolean session_only = (expires == NULL);
    if (expires != NULL) {
        expires_ms = g_date_time_to_unix(expires) * 1000;
    }
    SoupSameSitePolicy ss = soup_cookie_get_same_site_policy(cookie);
    const char *same_site = "Lax";
    switch (ss) {
        case SOUP_SAME_SITE_POLICY_NONE: same_site = "None"; break;
        case SOUP_SAME_SITE_POLICY_STRICT: same_site = "Strict"; break;
        case SOUP_SAME_SITE_POLICY_LAX:
        default: same_site = "Lax"; break;
    }

    /* Minimal JSON — values escaped conservatively. */
    gchar *name_e = g_strescape(name ? name : "", NULL);
    gchar *value_e = g_strescape(value ? value : "", NULL);
    gchar *domain_e = g_strescape(domain ? domain : "", NULL);
    gchar *path_e = g_strescape(path ? path : "/", NULL);
    gchar *json = g_strdup_printf(
        "{\"name\":\"%s\",\"value\":\"%s\",\"domain\":\"%s\",\"path\":\"%s\","
        "\"secure\":%s,\"httpOnly\":%s,\"sessionOnly\":%s,\"expiresDate\":%lld,"
        "\"sameSite\":\"%s\"}",
        name_e ? name_e : "",
        value_e ? value_e : "",
        domain_e ? domain_e : "",
        path_e ? path_e : "/",
        secure ? "true" : "false",
        http_only ? "true" : "false",
        session_only ? "true" : "false",
        (long long) expires_ms,
        same_site);
    g_free(name_e);
    g_free(value_e);
    g_free(domain_e);
    g_free(path_e);
    return json;
}

typedef struct {
    jlong handle;
} CookieOpData;

static void on_get_cookies_finished(
    GObject *source,
    GAsyncResult *result,
    gpointer user_data)
{
    CookieOpData *data = (CookieOpData *) user_data;
    WebKitCookieManager *cm = WEBKIT_COOKIE_MANAGER(source);
    GError *error = NULL;
    GList *cookies = webkit_cookie_manager_get_cookies_finish(cm, result, &error);

    GString *json = g_string_new("[");
    gboolean first = TRUE;
    if (error == NULL && cookies != NULL) {
        for (GList *l = cookies; l != NULL; l = l->next) {
            SoupCookie *cookie = (SoupCookie *) l->data;
            gchar *entry = cookie_to_json(cookie);
            if (!first) g_string_append_c(json, ',');
            g_string_append(json, entry);
            g_free(entry);
            first = FALSE;
            soup_cookie_free(cookie);
        }
        g_list_free(cookies);
    }
    if (error != NULL) g_error_free(error);
    g_string_append_c(json, ']');

    JNIEnv *env = compose_webview_get_env();
    if (env != NULL) {
        compose_webview_ensure_bridge_methods(env);
        if (compose_webview_bridge_class() != NULL && compose_webview_on_cookies() != NULL) {
            jstring jjson = (*env)->NewStringUTF(env, json->str);
            (*env)->CallStaticVoidMethod(
                env, compose_webview_bridge_class(), compose_webview_on_cookies(), data->handle, jjson);
            (*env)->DeleteLocalRef(env, jjson);
            if ((*env)->ExceptionCheck(env)) {
                (*env)->ExceptionClear(env);
            }
        }
    }
    g_string_free(json, TRUE);
    g_free(data);
}

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_linux_WebKitLinuxBridge_nativeGetCookies(
    JNIEnv *env, jclass clazz, jlong handle, jstring url_str)
{
    (void) clazz;
    ComposeWebViewState *state = compose_webview_state_from_handle(handle);
    WebKitCookieManager *cm = cookie_manager_for(state);
    if (cm == NULL || url_str == NULL) {
        compose_webview_ensure_bridge_methods(env);
        if (compose_webview_bridge_class() != NULL && compose_webview_on_cookies() != NULL) {
            jstring empty = (*env)->NewStringUTF(env, "[]");
            (*env)->CallStaticVoidMethod(
                env, compose_webview_bridge_class(), compose_webview_on_cookies(), handle, empty);
            (*env)->DeleteLocalRef(env, empty);
        }
        return;
    }
    const char *url = (*env)->GetStringUTFChars(env, url_str, NULL);
    if (url == NULL) return;
    CookieOpData *data = g_new0(CookieOpData, 1);
    data->handle = handle;
    webkit_cookie_manager_get_cookies(cm, url, NULL, on_get_cookies_finished, data);
    (*env)->ReleaseStringUTFChars(env, url_str, url);
}

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_linux_WebKitLinuxBridge_nativeSetCookie(
    JNIEnv *env,
    jclass clazz,
    jlong handle,
    jstring name,
    jstring value,
    jstring domain,
    jstring path,
    jboolean secure,
    jboolean http_only,
    jlong expires_ms,
    jstring same_site)
{
    (void) clazz;
    ComposeWebViewState *state = compose_webview_state_from_handle(handle);
    WebKitCookieManager *cm = cookie_manager_for(state);
    if (cm == NULL || name == NULL || value == NULL) return;

    const char *c_name = (*env)->GetStringUTFChars(env, name, NULL);
    const char *c_value = (*env)->GetStringUTFChars(env, value, NULL);
    const char *c_domain = domain != NULL ? (*env)->GetStringUTFChars(env, domain, NULL) : NULL;
    const char *c_path = path != NULL ? (*env)->GetStringUTFChars(env, path, NULL) : "/";
    const char *c_ss = same_site != NULL ? (*env)->GetStringUTFChars(env, same_site, NULL) : NULL;

    if (c_name != NULL && c_value != NULL) {
        SoupCookie *cookie = soup_cookie_new(
            c_name,
            c_value,
            c_domain ? c_domain : "",
            c_path ? c_path : "/",
            -1);
        soup_cookie_set_secure(cookie, secure ? TRUE : FALSE);
        soup_cookie_set_http_only(cookie, http_only ? TRUE : FALSE);
        if (expires_ms > 0) {
            GDateTime *dt = g_date_time_new_from_unix_utc(expires_ms / 1000);
            if (dt != NULL) {
                soup_cookie_set_expires(cookie, dt);
                g_date_time_unref(dt);
            }
        }
        if (c_ss != NULL) {
            SoupSameSitePolicy policy = SOUP_SAME_SITE_POLICY_LAX;
            if (g_ascii_strcasecmp(c_ss, "None") == 0) {
                policy = SOUP_SAME_SITE_POLICY_NONE;
            } else if (g_ascii_strcasecmp(c_ss, "Strict") == 0) {
                policy = SOUP_SAME_SITE_POLICY_STRICT;
            }
            soup_cookie_set_same_site_policy(cookie, policy);
        }
        webkit_cookie_manager_add_cookie(cm, cookie, NULL, NULL, NULL);
        soup_cookie_free(cookie);
    }

    if (c_name != NULL) (*env)->ReleaseStringUTFChars(env, name, c_name);
    if (c_value != NULL) (*env)->ReleaseStringUTFChars(env, value, c_value);
    if (domain != NULL && c_domain != NULL) {
        (*env)->ReleaseStringUTFChars(env, domain, c_domain);
    }
    if (path != NULL && c_path != NULL) {
        (*env)->ReleaseStringUTFChars(env, path, c_path);
    }
    if (same_site != NULL && c_ss != NULL) {
        (*env)->ReleaseStringUTFChars(env, same_site, c_ss);
    }
}

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_linux_WebKitLinuxBridge_nativeRemoveAllCookies(
    JNIEnv *env, jclass clazz, jlong handle)
{
    (void) env; (void) clazz;
    ComposeWebViewState *state = compose_webview_state_from_handle(handle);
    WebKitCookieManager *cm = cookie_manager_for(state);
    if (cm == NULL) return;
    webkit_cookie_manager_delete_all_cookies(cm);
}

typedef struct {
    WebKitCookieManager *cm;
} DeleteCookiesCtx;

static void on_delete_cookies_for_url_fetched(
    GObject *source,
    GAsyncResult *result,
    gpointer user_data)
{
    DeleteCookiesCtx *ctx = (DeleteCookiesCtx *) user_data;
    WebKitCookieManager *cm = WEBKIT_COOKIE_MANAGER(source);
    GError *error = NULL;
    GList *cookies = webkit_cookie_manager_get_cookies_finish(cm, result, &error);
    if (error != NULL) {
        g_error_free(error);
    } else if (cookies != NULL) {
        for (GList *l = cookies; l != NULL; l = l->next) {
            SoupCookie *cookie = (SoupCookie *) l->data;
            webkit_cookie_manager_delete_cookie(ctx->cm, cookie, NULL, NULL, NULL);
            soup_cookie_free(cookie);
        }
        g_list_free(cookies);
    }
    g_object_unref(ctx->cm);
    g_free(ctx);
}

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_linux_WebKitLinuxBridge_nativeRemoveCookiesForUrl(
    JNIEnv *env, jclass clazz, jlong handle, jstring url_str)
{
    (void) clazz;
    ComposeWebViewState *state = compose_webview_state_from_handle(handle);
    WebKitCookieManager *cm = cookie_manager_for(state);
    if (cm == NULL || url_str == NULL) return;

    const char *url = (*env)->GetStringUTFChars(env, url_str, NULL);
    if (url == NULL) return;

    DeleteCookiesCtx *ctx = g_new0(DeleteCookiesCtx, 1);
    ctx->cm = g_object_ref(cm);
    webkit_cookie_manager_get_cookies(
        cm, url, NULL, on_delete_cookies_for_url_fetched, ctx);
    (*env)->ReleaseStringUTFChars(env, url_str, url);
}

