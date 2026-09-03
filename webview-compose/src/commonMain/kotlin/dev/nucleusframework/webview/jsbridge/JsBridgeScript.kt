package dev.nucleusframework.webview.jsbridge

/**
 * Builds the `window.<name>` bridge object that JS uses to call Kotlin.
 *
 * Single source of truth for the JS half of the bridge: a platform only
 * supplies [postMessageBody], the statements that hand a serialized
 * [JsMessage] to its native transport. Desktop backends inject the result at
 * document start (native user script), so the object exists before any page
 * script runs; other platforms evaluate it after load.
 *
 * The definition is idempotent — re-injecting keeps the pending callbacks of
 * an already installed bridge.
 */
internal fun jsBridgeObjectScript(
    name: String,
    postMessageBody: String,
): String =
    """
    if (typeof window.$name === 'undefined') {
        window.$name = {
            callbacks: {},
            callbackId: 0,
            callNative: function (methodName, params, callback) {
                var message = {
                    methodName: methodName,
                    params: params,
                    callbackId: callback ? window.$name.callbackId++ : -1
                };
                if (callback) {
                    window.$name.callbacks[message.callbackId] = callback;
                }
                window.$name.postMessage(JSON.stringify(message));
            },
            onCallback: function (callbackId, data) {
                var callback = window.$name.callbacks[callbackId];
                if (callback) {
                    callback(data);
                    delete window.$name.callbacks[callbackId];
                }
            },
            postMessage: function (message) { $postMessageBody }
        };
    }
    """.trimIndent()
