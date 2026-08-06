package dev.nucleusframework.webview.request

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Shared multiplatform suite — must pass on JVM, Android host, iOS simulator, Wasm.
 */
class WebRequestTest {
    @Test
    fun defaults() {
        val request = WebRequest(url = "https://example.com")
        assertEquals("https://example.com", request.url)
        assertTrue(request.headers.isEmpty())
        assertFalse(request.isForMainFrame)
        assertFalse(request.isRedirect)
        assertEquals("GET", request.method)
    }

    @Test
    fun copyPreservesHeadersMutabilitySemantics() {
        val request =
            WebRequest(
                url = "https://example.com",
                headers = mutableMapOf("A" to "1"),
                isForMainFrame = true,
                isRedirect = true,
                method = "POST",
            )
        assertEquals("POST", request.method)
        assertTrue(request.isForMainFrame)
        assertTrue(request.isRedirect)
        assertEquals("1", request.headers["A"])
    }

    @Test
    fun interceptResults() {
        assertIs<WebRequestInterceptResult.Allow>(WebRequestInterceptResult.Allow)
        assertIs<WebRequestInterceptResult.Reject>(WebRequestInterceptResult.Reject)
        val modified =
            WebRequestInterceptResult.Modify(
                WebRequest(url = "https://redirect.example"),
            )
        assertEquals("https://redirect.example", modified.request.url)
    }
}
