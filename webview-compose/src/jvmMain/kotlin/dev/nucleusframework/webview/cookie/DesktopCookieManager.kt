package dev.nucleusframework.webview.cookie

/**
 * No-op cookie manager for desktop.
 */
internal class DesktopCookieManager : CookieManager {
    override suspend fun setCookie(url: String, cookie: Cookie) = Unit

    override suspend fun getCookies(url: String): List<Cookie> = emptyList()

    override suspend fun removeAllCookies() = Unit

    override suspend fun removeCookies(url: String) = Unit
}
