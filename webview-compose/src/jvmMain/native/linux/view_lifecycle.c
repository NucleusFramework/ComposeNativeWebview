#include "compose_webview_internal.h"

/* The relay's script world and message handler, and the events it trades with the page's shim. */
#define IPC_RELAY_WORLD "nucleusIpcRelay"
#define IPC_RELAY_HANDLER "nucleusIpcRelay"
#define IPC_MESSAGE_EVENT "__nucleusIpcMessage"
#define IPC_RELAY_STARTED_EVENT "__nucleusIpcRelayStarted"

ComposeWebViewState *compose_webview_state_from_handle(jlong handle) {
    if (handle == 0) return NULL;
    return (ComposeWebViewState *) (uintptr_t) handle;
}

jlong compose_webview_handle_from_view(WebKitWebView *view) {
    gpointer p = g_object_get_data(G_OBJECT(view), "compose-webview-state");
    return (jlong) (uintptr_t) p;
}

JNIEXPORT jlong JNICALL
Java_dev_nucleusframework_webview_web_linux_WebKitLinuxBridge_nativeCreate(
    JNIEnv *env,
    jclass clazz,
    jstring user_agent,
    jstring data_directory,
    jstring init_script,
    jstring js_bridge_script,
    jboolean incognito,
    jboolean enable_devtools,
    jboolean javascript_enabled,
    jdouble zoom_level,
    jboolean transparent,
    jfloat bg_r,
    jfloat bg_g,
    jfloat bg_b,
    jfloat bg_a)
{
    (void) clazz;
    compose_webview_ensure_bridge_methods(env);

    ComposeWebViewState *state = g_new0(ComposeWebViewState, 1);

    WebKitWebsiteDataManager *data_manager = NULL;
    if (incognito) {
        data_manager = webkit_website_data_manager_new_ephemeral();
    } else if (data_directory != NULL) {
        const char *dir = (*env)->GetStringUTFChars(env, data_directory, NULL);
        if (dir != NULL) {
            gchar *cache_dir = g_build_filename(dir, "cache", NULL);
            data_manager = webkit_website_data_manager_new(
                "base-data-directory", dir,
                "base-cache-directory", cache_dir,
                NULL);
            g_free(cache_dir);
            (*env)->ReleaseStringUTFChars(env, data_directory, dir);
        }
    }
    if (data_manager == NULL) {
        data_manager = webkit_website_data_manager_new_ephemeral();
    }

    state->context = webkit_web_context_new_with_website_data_manager(data_manager);
    g_object_unref(data_manager);

    state->ucm = webkit_user_content_manager_new();
    /* Connect BEFORE register to avoid racing the first postMessage. */
    state->ipc_handler = g_signal_connect(
        state->ucm,
        "script-message-received::ipc",
        G_CALLBACK(compose_webview_on_script_message),
        state);
    webkit_user_content_manager_register_script_message_handler(state->ucm, "ipc");

    /*
     * Always inject a small window.ipc shim so the Kotlin JS bridge can attach. It offers each
     * message to the relay below, which takes a message by cancelling its event. WebKit starts
     * the two scripts in no fixed order, so until the relay has started in a document, messages
     * wait for it. After that, a message the relay does not take goes straight to the "ipc"
     * handler and arrives without a sender: so it is between a page's document.open(), which
     * removes the relay's listener, and the document the page then writes, where the relay
     * starts again.
     */
    const char *ipc_shim =
        "(function () {"
        "  if (typeof window.ipc !== 'undefined') { return; }"
        "  var dispatch = window.dispatchEvent.bind(window);"
        "  var CustomEvent = window.CustomEvent;"
        "  var waiting = [];"
        "  function offer(body) {"
        "    return !dispatch(new CustomEvent('" IPC_MESSAGE_EVENT "',"
        "                                     { cancelable: true, detail: body }));"
        "  }"
        "  function post(body) {"
        "    if (offer(body)) { return; }"
        "    if (window.webkit && window.webkit.messageHandlers &&"
        "        window.webkit.messageHandlers.ipc) {"
        "      window.webkit.messageHandlers.ipc.postMessage(body);"
        "    }"
        "  }"
        "  var relayStarted = offer(null);"
        "  window.addEventListener('" IPC_RELAY_STARTED_EVENT "', function (e) {"
        "    e.stopImmediatePropagation();"
        "    relayStarted = true;"
        "    var held = waiting;"
        "    waiting = [];"
        "    for (var i = 0; i < held.length; i++) { post(held[i]); }"
        "  }, true);"
        "  window.ipc = {"
        "    postMessage: function(message) {"
        "      var body = (typeof message === 'string') ? message : JSON.stringify(message);"
        "      if (relayStarted) { post(body); } else { waiting.push(body); }"
        "    }"
        "  };"
        "})();";
    WebKitUserScript *shim = webkit_user_script_new(
        ipc_shim,
        WEBKIT_USER_CONTENT_INJECT_ALL_FRAMES,
        WEBKIT_USER_SCRIPT_INJECT_AT_DOCUMENT_START,
        NULL,
        NULL);
    webkit_user_content_manager_add_script(state->ucm, shim);
    webkit_user_script_unref(shim);

    /*
     * WebKitGTK reports nothing about who posted a script message, so the shim's messages go
     * through this relay. It runs in every frame, in a script world of its own: the page's
     * scripts share the DOM with it but none of its JavaScript objects, so they can neither
     * reach the handler registered in that world nor change what the relay reads there. It
     * stamps each message with the origin of its frame's document and whether that frame is
     * the top one. A page reaches only the relay of its own frame, through an event on its
     * `window`, which a document of another origin cannot dispatch.
     */
    const char *ipc_relay =
        "(function () {"
        "  var handlers = window.webkit && window.webkit.messageHandlers;"
        "  var handler = handlers && handlers." IPC_RELAY_HANDLER ";"
        "  if (!handler) { return; }"
        "  var post = handler.postMessage.bind(handler);"
        "  window.addEventListener('" IPC_MESSAGE_EVENT "', function (e) {"
        "    e.stopImmediatePropagation();"
        "    e.preventDefault();"
        "    if (typeof e.detail === 'string') {"
        "      post({ origin: window.origin, isMainFrame: window.top === window, body: e.detail });"
        "    }"
        "  }, true);"
        "  window.dispatchEvent(new CustomEvent('" IPC_RELAY_STARTED_EVENT "'));"
        "})();";
    /* Connect BEFORE register, as for "ipc" above. */
    state->ipc_relay_handler = g_signal_connect(
        state->ucm,
        "script-message-received::" IPC_RELAY_HANDLER,
        G_CALLBACK(compose_webview_on_relayed_script_message),
        state);
    webkit_user_content_manager_register_script_message_handler_in_world(
        state->ucm, IPC_RELAY_HANDLER, IPC_RELAY_WORLD);
    WebKitUserScript *relay = webkit_user_script_new_for_world(
        ipc_relay,
        WEBKIT_USER_CONTENT_INJECT_ALL_FRAMES,
        WEBKIT_USER_SCRIPT_INJECT_AT_DOCUMENT_START,
        IPC_RELAY_WORLD,
        NULL,
        NULL);
    webkit_user_content_manager_add_script(state->ucm, relay);
    webkit_user_script_unref(relay);

    /*
     * JS bridge object, built once in Kotlin. Injected at document start so
     * page scripts can call it without waiting for a post-load injection.
     */
    if (js_bridge_script != NULL) {
        const char *bridge_src = (*env)->GetStringUTFChars(env, js_bridge_script, NULL);
        if (bridge_src != NULL && bridge_src[0] != '\0') {
            WebKitUserScript *bridge = webkit_user_script_new(
                bridge_src,
                WEBKIT_USER_CONTENT_INJECT_ALL_FRAMES,
                WEBKIT_USER_SCRIPT_INJECT_AT_DOCUMENT_START,
                NULL,
                NULL);
            webkit_user_content_manager_add_script(state->ucm, bridge);
            webkit_user_script_unref(bridge);
        }
        if (bridge_src != NULL) {
            (*env)->ReleaseStringUTFChars(env, js_bridge_script, bridge_src);
        }
    }

    /*
     * Opaque mode: force a solid page background. Many pages (and about:blank)
     * leave html/body transparent; without this the Compose clear-through
     * NativeView hole shows UI underneath the WebView.
     */
    if (!transparent) {
        WebKitUserStyleSheet *sheet = webkit_user_style_sheet_new(
            "html, body { background-color: #ffffff !important; }",
            WEBKIT_USER_CONTENT_INJECT_ALL_FRAMES,
            WEBKIT_USER_STYLE_LEVEL_USER,
            NULL,
            NULL);
        webkit_user_content_manager_add_style_sheet(state->ucm, sheet);
        webkit_user_style_sheet_unref(sheet);
    }

    if (init_script != NULL) {
        const char *src = (*env)->GetStringUTFChars(env, init_script, NULL);
        if (src != NULL && src[0] != '\0') {
            WebKitUserScript *user_script = webkit_user_script_new(
                src,
                WEBKIT_USER_CONTENT_INJECT_TOP_FRAME,
                WEBKIT_USER_SCRIPT_INJECT_AT_DOCUMENT_START,
                NULL,
                NULL);
            webkit_user_content_manager_add_script(state->ucm, user_script);
            webkit_user_script_unref(user_script);
        }
        if (src != NULL) {
            (*env)->ReleaseStringUTFChars(env, init_script, src);
        }
    }

    WebKitSettings *settings = webkit_settings_new();
    webkit_settings_set_enable_javascript(settings, javascript_enabled ? TRUE : FALSE);
    webkit_settings_set_enable_developer_extras(settings, enable_devtools ? TRUE : FALSE);
    webkit_settings_set_javascript_can_access_clipboard(settings, TRUE);
    webkit_settings_set_enable_write_console_messages_to_stdout(settings, FALSE);
    if (user_agent != NULL) {
        const char *ua = (*env)->GetStringUTFChars(env, user_agent, NULL);
        if (ua != NULL && ua[0] != '\0') {
            webkit_settings_set_user_agent(settings, ua);
        }
        if (ua != NULL) {
            (*env)->ReleaseStringUTFChars(env, user_agent, ua);
        }
    }

    state->web_view = WEBKIT_WEB_VIEW(g_object_new(
        WEBKIT_TYPE_WEB_VIEW,
        "web-context", state->context,
        "user-content-manager", state->ucm,
        "settings", settings,
        NULL));
    g_object_unref(settings);

    /* Strong floating-ref so the widget survives until nativeRelease. */
    g_object_ref_sink(G_OBJECT(state->web_view));

    if (zoom_level > 0.0) {
        webkit_web_view_set_zoom_level(state->web_view, zoom_level);
    }

    /* Opaque browser default = solid white when transparent is off. */
    GdkRGBA bg;
    if (transparent) {
        bg.red = bg_r;
        bg.green = bg_g;
        bg.blue = bg_b;
        bg.alpha = bg_a;
    } else {
        bg.red = (bg_a >= 1.0f) ? bg_r : 1.0;
        bg.green = (bg_a >= 1.0f) ? bg_g : 1.0;
        bg.blue = (bg_a >= 1.0f) ? bg_b : 1.0;
        bg.alpha = 1.0;
    }
    webkit_web_view_set_background_color(state->web_view, &bg);
    gtk_widget_set_opacity(GTK_WIDGET(state->web_view), 1.0);

    g_object_set_data(G_OBJECT(state->web_view), "compose-webview-state", state);

    state->decide_policy_handler = g_signal_connect(
        state->web_view,
        "decide-policy",
        G_CALLBACK(compose_webview_on_decide_policy),
        state);

    return (jlong) (uintptr_t) state;
}

JNIEXPORT jlong JNICALL
Java_dev_nucleusframework_webview_web_linux_WebKitLinuxBridge_nativeGetGtkWidget(
    JNIEnv *env, jclass clazz, jlong handle)
{
    (void) env; (void) clazz;
    ComposeWebViewState *state = compose_webview_state_from_handle(handle);
    if (state == NULL || state->web_view == NULL) return 0;
    return (jlong) (uintptr_t) GTK_WIDGET(state->web_view);
}

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_linux_WebKitLinuxBridge_nativeRelease(
    JNIEnv *env, jclass clazz, jlong handle)
{
    (void) env; (void) clazz;
    ComposeWebViewState *state = compose_webview_state_from_handle(handle);
    if (state == NULL) return;

    if (state->web_view != NULL) {
        if (state->decide_policy_handler != 0) {
            g_signal_handler_disconnect(state->web_view, state->decide_policy_handler);
            state->decide_policy_handler = 0;
        }
        g_object_set_data(G_OBJECT(state->web_view), "compose-webview-state", NULL);
        g_object_unref(state->web_view);
        state->web_view = NULL;
    }
    if (state->ucm != NULL) {
        if (state->ipc_handler != 0) {
            g_signal_handler_disconnect(state->ucm, state->ipc_handler);
            state->ipc_handler = 0;
        }
        if (state->ipc_relay_handler != 0) {
            g_signal_handler_disconnect(state->ucm, state->ipc_relay_handler);
            state->ipc_relay_handler = 0;
        }
        g_object_unref(state->ucm);
        state->ucm = NULL;
    }
    if (state->context != NULL) {
        g_object_unref(state->context);
        state->context = NULL;
    }
    g_free(state);
}

