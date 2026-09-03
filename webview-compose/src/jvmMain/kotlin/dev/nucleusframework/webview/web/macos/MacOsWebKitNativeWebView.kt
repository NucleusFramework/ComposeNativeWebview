package dev.nucleusframework.webview.web.macos

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.nucleusframework.webview.web.NativeWebView
import dev.nucleusframework.window.tao.NucleusPlatformView
import kotlinx.coroutines.CompletableDeferred

/**
 * macOS [NativeWebView] backed by a real WKWebView, embeddable via
 * [NucleusPlatformView.NsView] / Nucleus [dev.nucleusframework.window.tao.NativeView].
 *
 * Requires the Tao window backend.
 */
class MacOsWebKitNativeWebView(
    customUserAgent: String? = null,
    dataDirectory: String? = null,
    initScript: String? = null,
    /** JS bridge bootstrap injected at document start in all frames. */
    jsBridgeScript: String? = null,
    incognito: Boolean = false,
    enableDevtools: Boolean = false,
    javascriptEnabled: Boolean = true,
    zoomLevel: Double = 1.0,
    transparent: Boolean = false,
    backgroundColor: Color = Color.White,
) : NativeWebView() {
    private val handle: Long
    private val nsViewHandle: Long
    private var released = false

    init {
        require(WebKitMacOsBridge.isLoaded) {
            "compose_webview_macos native library is not available"
        }
        val effective =
            if (transparent) {
                backgroundColor
            } else if (backgroundColor.alpha < 1f) {
                Color.White
            } else {
                backgroundColor.copy(alpha = 1f)
            }
        val argb = effective.toArgb()
        val a = ((argb ushr 24) and 0xFF) / 255f
        val r = ((argb ushr 16) and 0xFF) / 255f
        val g = ((argb ushr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        handle = WebKitMacOsBridge.nativeCreate(
            userAgent = customUserAgent?.trim()?.takeIf { it.isNotEmpty() },
            dataDirectory = dataDirectory?.trim()?.takeIf { it.isNotEmpty() },
            initScript = initScript?.trim()?.takeIf { it.isNotEmpty() },
            jsBridgeScript = jsBridgeScript?.trim()?.takeIf { it.isNotEmpty() },
            incognito = incognito,
            enableDevtools = enableDevtools,
            javascriptEnabled = javascriptEnabled,
            zoomLevel = zoomLevel,
            transparent = transparent,
            bgR = r,
            bgG = g,
            bgB = b,
            bgA = a,
        )
        require(handle != 0L) { "Failed to create WKWebView" }
        nsViewHandle = WebKitMacOsBridge.nativeGetNsView(handle)
        require(nsViewHandle != 0L) { "Failed to get NSView handle" }
    }

    /** Creates the [NucleusPlatformView] used by NativeView embedding. */
    fun asPlatformView(): NucleusPlatformView.NsView =
        object : NucleusPlatformView.NsView {
            override val nsViewHandle: Long
                get() = this@MacOsWebKitNativeWebView.nsViewHandle

            override fun dispose() {
                // Lifecycle owned by NativeWebView.destroy(); NativeView
                // also calls dispose — keep it idempotent.
            }
        }

    override fun isReady(): Boolean = !released && handle != 0L

    override fun isLoading(): Boolean =
        if (!isReady()) false else WebKitMacOsBridge.nativeIsLoading(handle)

    override fun getCurrentUrl(): String? =
        if (!isReady()) null else WebKitMacOsBridge.nativeCurrentUrl(handle)

    override fun getTitle(): String? =
        if (!isReady()) null else WebKitMacOsBridge.nativeGetTitle(handle)

    override fun canGoBack(): Boolean =
        if (!isReady()) false else WebKitMacOsBridge.nativeCanGoBack(handle)

    override fun canGoForward(): Boolean =
        if (!isReady()) false else WebKitMacOsBridge.nativeCanGoForward(handle)

    override fun loadUrl(url: String, additionalHttpHeaders: Map<String, String>) {
        if (!isReady()) return
        if (additionalHttpHeaders.isEmpty()) {
            WebKitMacOsBridge.nativeLoadUrl(handle, url)
        } else {
            val names = additionalHttpHeaders.keys.toTypedArray()
            val values = additionalHttpHeaders.values.toTypedArray()
            WebKitMacOsBridge.nativeLoadUrlWithHeaders(handle, url, names, values)
        }
    }

    override fun loadHtml(html: String) {
        if (!isReady()) return
        WebKitMacOsBridge.nativeLoadHtml(handle, html, null)
    }

    fun loadHtml(html: String, baseUri: String?) {
        if (!isReady()) return
        WebKitMacOsBridge.nativeLoadHtml(handle, html, baseUri)
    }

    override fun goBack() {
        if (!isReady()) return
        WebKitMacOsBridge.nativeGoBack(handle)
    }

    override fun goForward() {
        if (!isReady()) return
        WebKitMacOsBridge.nativeGoForward(handle)
    }

    override fun reload() {
        if (!isReady()) return
        WebKitMacOsBridge.nativeReload(handle)
    }

    override fun stopLoading() {
        if (!isReady()) return
        WebKitMacOsBridge.nativeStopLoading(handle)
    }

    override fun evaluateJavaScript(script: String, callback: (String) -> Unit) {
        if (!isReady()) {
            callback("")
            return
        }
        WebKitMacOsBridge.registerJsCallback(handle, callback)
        WebKitMacOsBridge.nativeEvaluateJavaScript(handle, script)
    }

    override fun drainIpcMessages(): List<String> =
        if (!isReady()) emptyList() else WebKitMacOsBridge.drainIpcMessages(handle)

    override fun addNavigateListener(listener: (String) -> Boolean) {
        if (!isReady()) return
        WebKitMacOsBridge.addNavigateListener(handle, listener)
    }

    override fun removeNavigateListener(listener: (String) -> Boolean) {
        if (!isReady()) return
        WebKitMacOsBridge.removeNavigateListener(handle, listener)
    }

    override fun captureScreenshotNative(): ByteArray? = null

    suspend fun captureScreenshotAsync(): ByteArray? {
        if (!isReady()) return null
        val deferred = CompletableDeferred<ByteArray?>()
        WebKitMacOsBridge.registerScreenshotDeferred(handle, deferred)
        WebKitMacOsBridge.nativeCaptureScreenshot(handle)
        return deferred.await()
    }

    suspend fun getCookiesJson(url: String): String {
        if (!isReady()) return "[]"
        val deferred = CompletableDeferred<String>()
        WebKitMacOsBridge.registerCookieDeferred(handle, deferred)
        WebKitMacOsBridge.nativeGetCookies(handle, url)
        return deferred.await()
    }

    fun setCookieNative(
        name: String,
        value: String,
        domain: String?,
        path: String?,
        secure: Boolean,
        httpOnly: Boolean,
        expiresMs: Long,
        sameSite: String?,
    ) {
        if (!isReady()) return
        WebKitMacOsBridge.nativeSetCookie(
            handle, name, value, domain, path, secure, httpOnly, expiresMs, sameSite,
        )
    }

    fun removeAllCookiesNative() {
        if (!isReady()) return
        WebKitMacOsBridge.nativeRemoveAllCookies(handle)
    }

    fun removeCookiesForUrlNative(url: String) {
        if (!isReady()) return
        WebKitMacOsBridge.nativeRemoveCookiesForUrl(handle, url)
    }

    fun setZoomLevel(zoom: Double) {
        if (!isReady()) return
        WebKitMacOsBridge.nativeSetZoomLevel(handle, zoom)
    }

    override fun openDevTools() {
        if (!isReady()) return
        WebKitMacOsBridge.nativeOpenDevTools(handle)
    }

    override fun closeDevTools() {
        if (!isReady()) return
        WebKitMacOsBridge.nativeCloseDevTools(handle)
    }

    override fun focus() {
        if (!isReady()) return
        WebKitMacOsBridge.nativeFocus(handle)
    }

    override fun destroy() {
        if (released) return
        released = true
        WebKitMacOsBridge.clearHandle(handle)
        WebKitMacOsBridge.nativeRelease(handle)
    }
}
