package dev.nucleusframework.webview.cookie

import dev.nucleusframework.webview.util.KLogger
import dev.nucleusframework.webview.web.NativeWebView
import dev.nucleusframework.webview.web.linux.LinuxWebKitNativeWebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Desktop cookie manager. On Linux, backed by WebKit2GTK's cookie manager.
 * No-op on other desktop platforms.
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

    override suspend fun setCookie(url: String, cookie: Cookie) {
        val linux = nativeWebView as? LinuxWebKitNativeWebView ?: return
        withContext(Dispatchers.Main) {
            KLogger.d(tag = "DesktopCookieManager") { "setCookie url=$url name=${cookie.name}" }
            val domain =
                cookie.domain
                    ?: runCatching { java.net.URI(url).host }.getOrNull()
            linux.setCookieNative(
                name = cookie.name,
                value = cookie.value,
                domain = domain,
                path = cookie.path ?: "/",
                secure = cookie.isSecure == true,
                httpOnly = cookie.isHttpOnly == true,
                expiresMs = cookie.expiresDate ?: 0L,
                sameSite =
                    when (cookie.sameSite) {
                        Cookie.HTTPCookieSameSitePolicy.NONE -> "None"
                        Cookie.HTTPCookieSameSitePolicy.STRICT -> "Strict"
                        Cookie.HTTPCookieSameSitePolicy.LAX -> "Lax"
                        null -> null
                    },
            )
        }
    }

    override suspend fun getCookies(url: String): List<Cookie> {
        val linux = nativeWebView as? LinuxWebKitNativeWebView ?: return emptyList()
        return withContext(Dispatchers.Main) {
            runCatching {
                val raw = linux.getCookiesJson(url)
                json.decodeFromString<List<NativeCookieDto>>(raw).map { it.toCookie() }
            }.getOrElse {
                KLogger.e(it, tag = "DesktopCookieManager") { "getCookies failed url=$url" }
                emptyList()
            }
        }
    }

    override suspend fun removeAllCookies() {
        val linux = nativeWebView as? LinuxWebKitNativeWebView ?: return
        withContext(Dispatchers.Main) {
            runCatching { linux.removeAllCookiesNative() }
                .onFailure { KLogger.e(it, tag = "DesktopCookieManager") { "removeAllCookies failed" } }
        }
    }

    override suspend fun removeCookies(url: String) {
        val linux = nativeWebView as? LinuxWebKitNativeWebView ?: return
        withContext(Dispatchers.Main) {
            runCatching { linux.removeCookiesForUrlNative(url) }
                .onFailure {
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
