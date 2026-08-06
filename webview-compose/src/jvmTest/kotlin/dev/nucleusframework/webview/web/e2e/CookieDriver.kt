package dev.nucleusframework.webview.web.e2e

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import dev.nucleusframework.webview.cookie.Cookie
import dev.nucleusframework.webview.web.WebViewState
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.delay

@Composable
internal fun CookieDriver(
    state: WebViewState,
    onFailure: (String) -> Unit,
    onDone: () -> Unit,
) {
    LaunchedEffect(Unit) {
        try {
            awaitWebViewReady(state)
            val url = "https://e2e.local/"
            state.cookieManager.removeAllCookies()
            delay(200)
            state.cookieManager.setCookie(
                url,
                Cookie(
                    name = "e2e_cookie",
                    value = "v1",
                    domain = "e2e.local",
                    path = "/",
                    isSecure = false,
                    isHttpOnly = false,
                ),
            )
            awaitUntil("cookie present") {
                state.cookieManager.getCookies(url).any { it.name == "e2e_cookie" && it.value == "v1" }
            }
            val cookies = state.cookieManager.getCookies(url)
            assertTrue(cookies.any { it.name == "e2e_cookie" }, "cookie not found: $cookies")
            state.cookieManager.removeCookies(url)
            delay(300)
            val after = state.cookieManager.getCookies(url)
            assertFalse(after.any { it.name == "e2e_cookie" }, "cookie should be removed, still: $after")
            println("[e2e] cookies set/get/remove OK")
            onDone()
        } catch (t: Throwable) {
            onFailure("cookies: ${t.message}")
            onDone()
        }
    }
}
