package dev.nucleusframework.webview.cookie

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Shared multiplatform suite — must pass on JVM, Android host, iOS simulator, Wasm.
 */
class CookieTest {
    @Test
    fun toStringMinimal() {
        val cookie = Cookie(name = "sid", value = "abc")
        assertEquals("sid=abc;", cookie.toString())
    }

    @Test
    fun toStringIncludesOptionalAttributes() {
        val cookie =
            Cookie(
                name = "sid",
                value = "abc",
                domain = "example.com",
                path = "/",
                isSecure = true,
                isHttpOnly = true,
                sameSite = Cookie.HTTPCookieSameSitePolicy.LAX,
                maxAge = 3600,
            )
        val raw = cookie.toString()
        assertContains(raw, "sid=abc")
        assertContains(raw, "Domain=example.com")
        assertContains(raw, "Path=/")
        assertContains(raw, "Secure")
        assertContains(raw, "HttpOnly")
        assertContains(raw, "SameSite=LAX")
        assertContains(raw, "Max-Age=3600")
        assertTrue(raw.endsWith(";"))
    }

    @Test
    fun sessionOnlyFlagDoesNotChangeWireFormatByItself() {
        val session = Cookie(name = "a", value = "b", isSessionOnly = true)
        val sticky = Cookie(name = "a", value = "b", isSessionOnly = false)
        assertEquals(session.toString(), sticky.toString())
        assertFalse(session.toString().contains("Session"))
    }
}
