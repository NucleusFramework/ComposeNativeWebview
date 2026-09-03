package dev.nucleusframework.webview.web.windows

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.nucleusframework.webview.web.NativeWebView
import dev.nucleusframework.window.tao.NucleusPlatformView
import kotlinx.coroutines.CompletableDeferred

/**
 * Windows [NativeWebView] backed by WebView2
 * (`CoreWebView2CompositionController` + DirectComposition).
 *
 * Embed via [NucleusPlatformView.HWnd] / Nucleus [dev.nucleusframework.window.tao.NativeView].
 * The platform handle is **not** a real child HWND — WebView2 paints through
 * a DComp tree owned by the native side; [NucleusPlatformView.HWnd.hwndHandle]
 * is always `0L` and positioning goes through [setBounds] / [setCornerRadius]
 * (same pattern as Nucleus `tao-demo` WebView tab).
 *
 * Requires the Tao window backend and WebView2 Runtime (bundled with Edge
 * on modern Windows).
 */
class WindowsWebView2NativeWebView(
    parentHwnd: Long,
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
    private var released = false

    init {
        require(WebView2WindowsBridge.isLoaded) {
            "compose_webview_windows native library is not available"
        }
        require(parentHwnd != 0L) { "parent HWND is required on Windows" }
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
        handle = WebView2WindowsBridge.nativeCreate(
            parentHwnd = parentHwnd,
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
        require(handle != 0L) {
            "Failed to create WebView2 (is WebView2 Runtime installed?)"
        }
    }

    /**
     * Creates the [NucleusPlatformView] used by NativeView embedding.
     *
     * [NucleusPlatformView.HWnd.hwndHandle] is intentionally `0L` so Tao's
     * SetParent/SetWindowPos path no-ops; layout is driven entirely via
     * [setBounds] / [setCornerRadius] on the DComp tree.
     */
    fun asPlatformView(): NucleusPlatformView.HWnd =
        object : NucleusPlatformView.HWnd {
            override val hwndHandle: Long = 0L

            override fun setBounds(xPx: Int, yPx: Int, widthPx: Int, heightPx: Int) {
                if (released) return
                WebView2WindowsBridge.nativeSetBounds(handle, xPx, yPx, widthPx, heightPx)
            }

            override fun setCornerRadius(radiusPx: Float) {
                if (released) return
                WebView2WindowsBridge.nativeSetCornerRadius(handle, radiusPx)
            }

            override fun dispose() {
                // Lifecycle owned by NativeWebView.destroy(); NativeView
                // also calls dispose — keep it idempotent.
            }
        }

    override fun isReady(): Boolean = !released && handle != 0L

    override fun isLoading(): Boolean =
        if (!isReady()) false else WebView2WindowsBridge.nativeIsLoading(handle)

    override fun getCurrentUrl(): String? =
        if (!isReady()) null else WebView2WindowsBridge.nativeCurrentUrl(handle)

    override fun getTitle(): String? =
        if (!isReady()) null else WebView2WindowsBridge.nativeGetTitle(handle)

    override fun canGoBack(): Boolean =
        if (!isReady()) false else WebView2WindowsBridge.nativeCanGoBack(handle)

    override fun canGoForward(): Boolean =
        if (!isReady()) false else WebView2WindowsBridge.nativeCanGoForward(handle)

    override fun loadUrl(url: String, additionalHttpHeaders: Map<String, String>) {
        if (!isReady()) return
        if (additionalHttpHeaders.isEmpty()) {
            WebView2WindowsBridge.nativeLoadUrl(handle, url)
        } else {
            val names = additionalHttpHeaders.keys.toTypedArray()
            val values = additionalHttpHeaders.values.toTypedArray()
            WebView2WindowsBridge.nativeLoadUrlWithHeaders(handle, url, names, values)
        }
    }

    override fun loadHtml(html: String) {
        if (!isReady()) return
        WebView2WindowsBridge.nativeLoadHtml(handle, html, null)
    }

    fun loadHtml(html: String, baseUri: String?) {
        if (!isReady()) return
        WebView2WindowsBridge.nativeLoadHtml(handle, html, baseUri)
    }

    override fun goBack() {
        if (!isReady()) return
        WebView2WindowsBridge.nativeGoBack(handle)
    }

    override fun goForward() {
        if (!isReady()) return
        WebView2WindowsBridge.nativeGoForward(handle)
    }

    override fun reload() {
        if (!isReady()) return
        WebView2WindowsBridge.nativeReload(handle)
    }

    override fun stopLoading() {
        if (!isReady()) return
        WebView2WindowsBridge.nativeStopLoading(handle)
    }

    override fun evaluateJavaScript(script: String, callback: (String) -> Unit) {
        if (!isReady()) {
            callback("")
            return
        }
        WebView2WindowsBridge.registerJsCallback(handle, callback)
        WebView2WindowsBridge.nativeEvaluateJavaScript(handle, script)
    }

    override fun drainIpcMessages(): List<String> =
        if (!isReady()) emptyList() else WebView2WindowsBridge.drainIpcMessages(handle)

    override fun addNavigateListener(listener: (String) -> Boolean) {
        if (!isReady()) return
        WebView2WindowsBridge.addNavigateListener(handle, listener)
    }

    override fun removeNavigateListener(listener: (String) -> Boolean) {
        if (!isReady()) return
        WebView2WindowsBridge.removeNavigateListener(handle, listener)
    }

    override fun captureScreenshotNative(): ByteArray? = null

    suspend fun captureScreenshotAsync(): ByteArray? {
        if (!isReady()) return null
        val deferred = CompletableDeferred<ByteArray?>()
        WebView2WindowsBridge.registerScreenshotDeferred(handle, deferred)
        WebView2WindowsBridge.nativeCaptureScreenshot(handle)
        return deferred.await()
    }

    suspend fun getCookiesJson(url: String): String {
        if (!isReady()) return "[]"
        val deferred = CompletableDeferred<String>()
        WebView2WindowsBridge.registerCookieDeferred(handle, deferred)
        WebView2WindowsBridge.nativeGetCookies(handle, url)
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
        WebView2WindowsBridge.nativeSetCookie(
            handle, name, value, domain, path, secure, httpOnly, expiresMs, sameSite,
        )
    }

    fun removeAllCookiesNative() {
        if (!isReady()) return
        WebView2WindowsBridge.nativeRemoveAllCookies(handle)
    }

    fun removeCookiesForUrlNative(url: String) {
        if (!isReady()) return
        WebView2WindowsBridge.nativeRemoveCookiesForUrl(handle, url)
    }

    fun setZoomLevel(zoom: Double) {
        if (!isReady()) return
        WebView2WindowsBridge.nativeSetZoomLevel(handle, zoom)
    }

    override fun openDevTools() {
        if (!isReady()) return
        WebView2WindowsBridge.nativeOpenDevTools(handle)
    }

    override fun closeDevTools() {
        if (!isReady()) return
        WebView2WindowsBridge.nativeCloseDevTools(handle)
    }

    override fun focus() {
        if (!isReady()) return
        WebView2WindowsBridge.nativeFocus(handle)
    }

    override fun destroy() {
        if (released) return
        released = true
        WebView2WindowsBridge.clearHandle(handle)
        WebView2WindowsBridge.nativeRelease(handle)
    }
}
