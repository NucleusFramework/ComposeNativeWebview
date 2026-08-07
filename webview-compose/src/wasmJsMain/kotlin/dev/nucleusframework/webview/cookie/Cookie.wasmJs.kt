@file:OptIn(ExperimentalWasmJsInterop::class)
package dev.nucleusframework.webview.cookie

import dev.nucleusframework.webview.util.KLogger
import kotlinx.browser.document

/**
 * Converts a timestamp to a cookie expiration date string in the browser's expected format.
 */
actual fun getCookieExpirationDate(
    expiresDate: Long
): String = js(
    //language=javascript
    "(new Date(expiresDate).toUTCString())"
)

@Suppress("FunctionName")
actual fun WebViewCookieManager(): CookieManager = WasmJsCookieManager

object WasmJsCookieManager : CookieManager {
    override suspend fun setCookie(
        url: String,
        cookie: Cookie
    ) {
        // Set the cookie using the document.cookie API
        KLogger.w(tag = "WasmJsCookieManager") {
            "removeCookies(url=$url): URL-specific cookie set is not supported in browser context. Cookies for different domain will be ignored."
        }
        document.cookie = cookie.toString()
    }

    override suspend fun getCookies(
        url: String
    ): List<Cookie> {
        val cookiesStr = document.cookie
        if (cookiesStr.isEmpty()) {
            return emptyList()
        }
        KLogger.w(tag = "WasmJsCookieManager") {
            "getCookies(url=$url): URL-specific cookies get is not supported in browser context. Returning only cookies for the current domain."
        }

        return cookiesStr.split(";").map { cookieStr ->
            val parts = cookieStr.trim().split("=", limit = 2)
            val name = parts[0]
            val value = if (parts.size > 1) {
                parts[1]
            } else {
                ""
            }

            Cookie(
                name = name,
                value = value
            )
        }
    }

    override suspend fun removeAllCookies() {
        val cookies = getCookies("")
        for (cookie in cookies) {
            // To delete a cookie, set it with an expired date
            document.cookie = buildString {
                append("${cookie.name}=")
                append("; path=/")
                append("; expires=Thu, 01 Jan 1970 00:00:00 GMT")
            }
        }
    }

    override suspend fun removeCookies(url: String) {
        // Browser document.cookie API does not support removing cookies for a specific URL/domain.
        // Falling back to removing all cookies. Consider using CookieStore API for finer control:
        // https://developer.mozilla.org/en-US/docs/Web/API/CookieStore
        KLogger.w(tag = "WasmJsCookieManager") {
            "removeCookies(url=$url): URL-specific cookie removal is not supported in browser context, removing all cookies instead"
        }
        removeAllCookies()
    }
}
