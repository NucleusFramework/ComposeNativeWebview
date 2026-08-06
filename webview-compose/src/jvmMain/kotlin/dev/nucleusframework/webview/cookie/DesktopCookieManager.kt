package dev.nucleusframework.webview.cookie

import dev.nucleusframework.webview.util.KLogger
import dev.nucleusframework.webview.web.NativeWebView
import dev.nucleusframework.webview.web.linux.LinuxWebKitNativeWebView
import dev.nucleusframework.webview.web.windows.WindowsWebView2NativeWebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Desktop cookie manager. Backed by WebKit2GTK on Linux and WebView2 on
 * Windows. No-op on other desktop platforms.
 */
internal class DesktopCookieManager : CookieManager {
    @Volatile
    private var nativeWebView: NativeWebView? = null

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    internal fun attach(webView: NativeWebView?) {
        this.nativeWebView = webView
    }

    private fun sameSiteString(cookie: Cookie): String? =
        when (cookie.sameSite) {
            Cookie.HTTPCookieSameSitePolicy.NONE -> "None"
            Cookie.HTTPCookieSameSitePolicy.STRICT -> "Strict"
            Cookie.HTTPCookieSameSitePolicy.LAX -> "Lax"
            null -> null
        }

    override suspend fun setCookie(url: String, cookie: Cookie) {
        val native = nativeWebView
        val domain =
            cookie.domain
                ?: runCatching { java.net.URI(url).host }.getOrNull()
        withContext(Dispatchers.Main) {
            KLogger.d(tag = "DesktopCookieManager") { "setCookie url=$url name=${cookie.name}" }
            when (native) {
                is LinuxWebKitNativeWebView ->
                    native.setCookieNative(
                        name = cookie.name,
                        value = cookie.value,
                        domain = domain,
                        path = cookie.path ?: "/",
                        secure = cookie.isSecure == true,
                        httpOnly = cookie.isHttpOnly == true,
                        expiresMs = cookie.expiresDate ?: 0L,
                        sameSite = sameSiteString(cookie),
                    )
                is WindowsWebView2NativeWebView ->
                    native.setCookieNative(
                        name = cookie.name,
                        value = cookie.value,
                        domain = domain,
                        path = cookie.path ?: "/",
                        secure = cookie.isSecure == true,
                        httpOnly = cookie.isHttpOnly == true,
                        expiresMs = cookie.expiresDate ?: 0L,
                        sameSite = sameSiteString(cookie),
                    )
                else -> Unit
            }
        }
    }

    override suspend fun getCookies(url: String): List<Cookie> {
        val native = nativeWebView
        return withContext(Dispatchers.Main) {
            runCatching {
                val raw =
                    when (native) {
                        is LinuxWebKitNativeWebView -> native.getCookiesJson(url)
                        is WindowsWebView2NativeWebView -> native.getCookiesJson(url)
                        else -> return@withContext emptyList()
                    }
                json.decodeFromString<List<NativeCookieDto>>(raw).map { it.toCookie() }
            }.getOrElse {
                KLogger.e(it, tag = "DesktopCookieManager") { "getCookies failed url=$url" }
                emptyList()
            }
        }
    }

    override suspend fun removeAllCookies() {
        val native = nativeWebView
        withContext(Dispatchers.Main) {
            runCatching {
                when (native) {
                    is LinuxWebKitNativeWebView -> native.removeAllCookiesNative()
                    is WindowsWebView2NativeWebView -> native.removeAllCookiesNative()
                    else -> Unit
                }
            }.onFailure { KLogger.e(it, tag = "DesktopCookieManager") { "removeAllCookies failed" } }
        }
    }

    override suspend fun removeCookies(url: String) {
        val native = nativeWebView
        withContext(Dispatchers.Main) {
            runCatching {
                when (native) {
                    is LinuxWebKitNativeWebView -> native.removeCookiesForUrlNative(url)
                    is WindowsWebView2NativeWebView -> native.removeCookiesForUrlNative(url)
                    else -> Unit
                }
            }.onFailure {
                KLogger.e(it, tag = "DesktopCookieManager") { "removeCookies failed url=$url" }
            }
        }
    }
}

@Serializable
private data class NativeCookieDto(
    val name: String,
    val value: String,
    val domain: String? = null,
    val path: String? = null,
    val secure: Boolean = false,
    @SerialName("httpOnly") val httpOnly: Boolean = false,
    @SerialName("sessionOnly") val sessionOnly: Boolean = true,
    @SerialName("expiresDate") val expiresDate: Long = 0,
    @SerialName("sameSite") val sameSite: String? = null,
) {
    fun toCookie(): Cookie =
        Cookie(
            name = name,
            value = value,
            domain = domain?.takeIf { it.isNotBlank() },
            path = path?.takeIf { it.isNotBlank() },
            expiresDate = expiresDate.takeIf { it > 0 },
            isSessionOnly = sessionOnly,
            isSecure = secure,
            isHttpOnly = httpOnly,
            sameSite =
                when (sameSite?.lowercase()) {
                    "none" -> Cookie.HTTPCookieSameSitePolicy.NONE
                    "strict" -> Cookie.HTTPCookieSameSitePolicy.STRICT
                    "lax" -> Cookie.HTTPCookieSameSitePolicy.LAX
                    else -> null
                },
        )
}
