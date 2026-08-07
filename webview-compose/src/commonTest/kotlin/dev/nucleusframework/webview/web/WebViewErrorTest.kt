package dev.nucleusframework.webview.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Shared multiplatform suite — must pass on JVM, Android host, iOS simulator, Wasm.
 */
class WebViewErrorTest {
    @Test
    fun holdsFields() {
        val error =
            WebViewError(
                code = -2,
                description = "net::ERR_FAILED",
                isFromMainFrame = false,
            )
        assertEquals(-2, error.code)
        assertEquals("net::ERR_FAILED", error.description)
        assertFalse(error.isFromMainFrame)
    }

    @Test
    fun equality() {
        val a = WebViewError(1, "x", true)
        val b = WebViewError(1, "x", true)
        val c = WebViewError(1, "y", true)
        assertEquals(a, b)
        assertTrue(a != c)
    }
}
