package dev.nucleusframework.webview.jsbridge

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Shared multiplatform suite — must pass on JVM, Android host, iOS simulator, Wasm.
 *
 * The script is the single source of truth for the JS half of the bridge: it is
 * evaluated after load on mobile/Wasm and injected as a native user script at
 * document start on desktop, so a regression here breaks every platform.
 */
class JsBridgeScriptTest {
    @Test
    fun definesBridgeUnderConfiguredName() {
        val script = jsBridgeObjectScript("myBridge", "noop();")

        assertTrue(script.contains("typeof window.myBridge === 'undefined'"))
        assertTrue(script.contains("window.myBridge.callbackId++"))
        assertTrue(script.contains("window.myBridge.postMessage(JSON.stringify(message));"))
        assertFalse(script.contains("kmpJsBridge"))
    }

    @Test
    fun routesPostMessageThroughPlatformBody() {
        val script =
            jsBridgeObjectScript(
                name = "kmpJsBridge",
                postMessageBody = "window.ipc.postMessage(message);",
            )

        assertTrue(
            script.contains("postMessage: function (message) { window.ipc.postMessage(message); }"),
        )
    }

    @Test
    fun keepsCallbackContractUsedByWebViewJsBridge() {
        val script = jsBridgeObjectScript("kmpJsBridge", "noop();")

        // WebViewJsBridge.onCallback evaluates window.<name>.onCallback(id, data).
        assertTrue(script.contains("onCallback: function (callbackId, data)"))
        // A call without a JS callback must not allocate a callback id.
        assertTrue(script.contains("callbackId: callback ? window.kmpJsBridge.callbackId++ : -1"))
    }
}
