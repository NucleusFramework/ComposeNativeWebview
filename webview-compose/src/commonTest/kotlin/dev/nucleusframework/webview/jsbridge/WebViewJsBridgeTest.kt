package dev.nucleusframework.webview.jsbridge

import dev.nucleusframework.webview.web.WebViewNavigator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Shared multiplatform suite — must pass on JVM, Android host, iOS simulator, Wasm.
 */
class WebViewJsBridgeTest {
    @Test
    fun defaultBridgeName() {
        val bridge = WebViewJsBridge()
        assertEquals("kmpJsBridge", bridge.jsBridgeName)
    }

    @Test
    fun customBridgeName() {
        val bridge = WebViewJsBridge(jsBridgeName = "myBridge")
        assertEquals("myBridge", bridge.jsBridgeName)
    }

    @Test
    fun registerDispatchAndClear() {
        val bridge = WebViewJsBridge()
        val seen = mutableListOf<String>()
        val handler =
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
            }

        bridge.register(handler)
        bridge.dispatch(
            JsMessage(callbackId = -1, methodName = "echo", params = """{"x":1}"""),
        )
        assertEquals(listOf("""{"x":1}"""), seen)

        bridge.clear()
        bridge.dispatch(
            JsMessage(callbackId = -1, methodName = "echo", params = """{"x":2}"""),
        )
        // cleared → no second dispatch
        assertEquals(listOf("""{"x":1}"""), seen)
    }

    @Test
    fun unregisterRemovesHandler() {
        val bridge = WebViewJsBridge()
        var calls = 0
        val handler =
            object : IJsMessageHandler {
                override fun methodName(): String = "ping"

                override fun handle(
                    message: JsMessage,
                    navigator: WebViewNavigator?,
                    callback: (String) -> Unit,
                ) {
                    calls++
                }
            }
        bridge.register(handler)
        bridge.unregister(handler)
        bridge.dispatch(JsMessage(callbackId = -1, methodName = "ping", params = "{}"))
        assertEquals(0, calls)
    }

    @Test
    fun processParamsAndDataToJsonString() {
        val handler =
            object : IJsMessageHandler {
                override fun methodName(): String = "typed"

                override fun handle(
                    message: JsMessage,
                    navigator: WebViewNavigator?,
                    callback: (String) -> Unit,
                ) = Unit
            }

        @kotlinx.serialization.Serializable
        data class Payload(val n: Int, val s: String)

        val message =
            JsMessage(
                callbackId = 1,
                methodName = "typed",
                params = """{"n":7,"s":"hi"}""",
            )
        val decoded = handler.processParams<Payload>(message)
        assertEquals(7, decoded.n)
        assertEquals("hi", decoded.s)

        val encoded = handler.dataToJsonString(Payload(n = 1, s = "x"))
        assertTrue(encoded.contains("\"n\":1"))
        assertTrue(encoded.contains("\"s\":\"x\""))
    }
}
