package dev.nucleusframework.webview.jsbridge

import dev.nucleusframework.webview.web.WebViewNavigator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Shared multiplatform suite — must pass on JVM, Android host, iOS simulator, Wasm.
 */
class JsMessageDispatcherTest {
    @Test
    fun dispatchesToRegisteredHandler() {
        val dispatcher = JsMessageDispatcher()
        val seen = mutableListOf<String>()

        dispatcher.registerJSHandler(
            object : IJsMessageHandler {
                override fun methodName(): String = "echo"

                override fun handle(
                    message: JsMessage,
                    navigator: WebViewNavigator?,
                    callback: (String) -> Unit,
                ) {
                    seen += message.params
                    callback("""{"ok":true}""")
                }
            },
        )

        var callbackPayload: String? = null
        dispatcher.dispatch(
            message =
                JsMessage(
                    callbackId = 1,
                    methodName = "echo",
                    params = """{"v":42}""",
                ),
            callback = { callbackPayload = it },
        )

        assertEquals(listOf("""{"v":42}"""), seen)
        assertEquals("""{"ok":true}""", callbackPayload)
    }

    @Test
    fun ignoresUnknownMethod() {
        val dispatcher = JsMessageDispatcher()
        var called = false
        dispatcher.dispatch(
            message = JsMessage(callbackId = 0, methodName = "missing", params = "{}"),
            callback = { called = true },
        )
        assertTrue(!called)
    }

    @Test
    fun unregisterStopsDispatch() {
        val dispatcher = JsMessageDispatcher()
        val handler =
            object : IJsMessageHandler {
                override fun methodName(): String = "ping"

                override fun handle(
                    message: JsMessage,
                    navigator: WebViewNavigator?,
                    callback: (String) -> Unit,
                ) {
                    callback("pong")
                }
            }
        dispatcher.registerJSHandler(handler)
        dispatcher.unregisterJSHandler(handler)

        var payload: String? = null
        dispatcher.dispatch(
            message = JsMessage(callbackId = 0, methodName = "ping", params = "{}"),
            callback = { payload = it },
        )
        assertEquals(null, payload)
    }
}
