package dev.nucleusframework.webview.cookie

import android.webkit.CookieManager as PlatformCookieManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine

internal object AndroidCookieManager : CookieManager {
    // Lazy: unit tests on the host JVM construct WebViewState without a real
    // android.webkit.CookieManager runtime (getInstance needs a process).
    private val cookieManager: PlatformCookieManager by lazy { PlatformCookieManager.getInstance() }

    override suspend fun setCookie(url: String, cookie: Cookie) {
        cookieManager.setCookie(url, cookie.toString())
        cookieManager.flush()
    }

    override suspend fun getCookies(url: String): List<Cookie> {
        val raw = cookieManager.getCookie(url).orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw
            .split(";")
            .mapNotNull { pair ->
                val trimmed = pair.trim()
                if (trimmed.isEmpty()) return@mapNotNull null
                val parts = trimmed.split("=", limit = 2)
                val name = parts.getOrNull(0)?.trim().orEmpty()
                if (name.isEmpty()) return@mapNotNull null
                val value = parts.getOrNull(1)?.trim().orEmpty()
                Cookie(name = name, value = value)
            }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun removeAllCookies() =
        suspendCancellableCoroutine { cont ->
            cookieManager.removeAllCookies { _ ->
                cookieManager.flush()
                cont.resume(Unit) {}
            }
        }

    override suspend fun removeCookies(url: String) {
        val cookies = getCookies(url)
        val host =
            runCatching {
                // android.net.Uri works without full URL parse edge cases
                android.net.Uri.parse(url).host
            }.getOrNull()
        for (cookie in cookies) {
            // Expire with and without Domain — Android matches on attributes.
            cookieManager.setCookie(url, "${cookie.name}=; Max-Age=0; Path=/")
            cookieManager.setCookie(
                url,
                "${cookie.name}=; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/",
            )
            if (!host.isNullOrBlank()) {
                cookieManager.setCookie(
                    url,
                    "${cookie.name}=; Max-Age=0; Path=/; Domain=$host",
                )
                cookieManager.setCookie(
                    url,
                    "${cookie.name}=; Max-Age=0; Path=/; Domain=.$host",
                )
            }
        }
        cookieManager.flush()
    }
}

