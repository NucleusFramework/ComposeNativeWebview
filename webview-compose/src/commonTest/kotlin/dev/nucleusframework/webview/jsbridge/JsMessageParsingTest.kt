package dev.nucleusframework.webview.jsbridge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Shared multiplatform suite — must pass on JVM, Android host, iOS simulator, Wasm.
 */
class JsMessageParsingTest {
    @Test
    fun parsesStandardBridgeMessage() {
        val raw =
            """
            {"callbackId":7,"methodName":"echo","params":"{\"x\":1}","type":"call"}
            """.trimIndent()

        val message = parseJsMessage(raw, expectedType = "call")
        assertNotNull(message)
        assertEquals(7, message.callbackId)
        assertEquals("echo", message.methodName)
        assertEquals("""{"x":1}""", message.params)
    }

    @Test
    fun rejectsUnexpectedType() {
        val raw =
            """
            {"callbackId":1,"methodName":"echo","params":"{}","type":"other"}
            """.trimIndent()

        assertNull(parseJsMessage(raw, expectedType = "call"))
    }

    @Test
    fun parsesWasmStyleActionMessage() {
        val raw =
            """
            {"action":"echo","params":{"hello":"world"}}
            """.trimIndent()

        val message = parseJsMessage(raw)
        assertNotNull(message)
        assertEquals("echo", message.methodName)
        assertEquals(0, message.callbackId)
        assertEquals("""{"hello":"world"}""", message.params)
    }

    @Test
    fun returnsNullForInvalidJson() {
        assertNull(parseJsMessage("not-json"))
    }

    @Test
    fun returnsNullWhenMethodMissing() {
        assertNull(parseJsMessage("""{"callbackId":1,"params":"{}"}"""))
    }
}
