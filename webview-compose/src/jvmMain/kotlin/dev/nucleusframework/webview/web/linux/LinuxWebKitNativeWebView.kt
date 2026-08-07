package dev.nucleusframework.webview.web.linux

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.nucleusframework.webview.web.NativeWebView
import dev.nucleusframework.window.tao.NucleusPlatformView
import kotlinx.coroutines.CompletableDeferred

/**
 * Linux [NativeWebView] backed by a real WebKit2GTK widget, embeddable
 * via [NucleusPlatformView.GtkWidget] / Nucleus [dev.nucleusframework.window.tao.NativeView].
 *
 * Requires the Tao window backend.
 */
class LinuxWebKitNativeWebView(
    customUserAgent: String? = null,
    dataDirectory: String? = null,
    initScript: String? = null,
    incognito: Boolean = false,
    enableDevtools: Boolean = false,
    javascriptEnabled: Boolean = true,
    zoomLevel: Double = 1.0,
    transparent: Boolean = false,
    backgroundColor: Color = Color.White,
) : NativeWebView() {
    private val handle: Long
    private val gtkWidgetHandle: Long
    private var released = false

    init {
        require(WebKitLinuxBridge.isLoaded) {
            "compose_webview_linux native library is not available"
        }
        // Non-transparent: always fully opaque so pages look like a normal browser.
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
        handle = WebKitLinuxBridge.nativeCreate(
            userAgent = customUserAgent?.trim()?.takeIf { it.isNotEmpty() },
            dataDirectory = dataDirectory?.trim()?.takeIf { it.isNotEmpty() },
            initScript = initScript?.trim()?.takeIf { it.isNotEmpty() },
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
        require(handle != 0L) { "Failed to create WebKitWebView" }
        gtkWidgetHandle = WebKitLinuxBridge.nativeGetGtkWidget(handle)
        require(gtkWidgetHandle != 0L) { "Failed to get GtkWidget handle" }
    }

    /** Creates the [NucleusPlatformView] used by NativeView embedding. */
    fun asPlatformView(): NucleusPlatformView.GtkWidget =
        object : NucleusPlatformView.GtkWidget {
            override val gtkWidgetHandle: Long
                get() = this@LinuxWebKitNativeWebView.gtkWidgetHandle

            override fun dispose() {
                // Lifecycle owned by NativeWebView.destroy(); NativeView
                // also calls dispose — keep it idempotent.
            }
        }

    override fun isReady(): Boolean = !released && handle != 0L

    override fun isLoading(): Boolean =
        if (!isReady()) false else WebKitLinuxBridge.nativeIsLoading(handle)

    override fun getCurrentUrl(): String? =
        if (!isReady()) null else WebKitLinuxBridge.nativeCurrentUrl(handle)

    override fun getTitle(): String? =
        if (!isReady()) null else WebKitLinuxBridge.nativeGetTitle(handle)

    override fun canGoBack(): Boolean =
        if (!isReady()) false else WebKitLinuxBridge.nativeCanGoBack(handle)

    override fun canGoForward(): Boolean =
        if (!isReady()) false else WebKitLinuxBridge.nativeCanGoForward(handle)

    override fun loadUrl(url: String, additionalHttpHeaders: Map<String, String>) {
        if (!isReady()) return
        if (additionalHttpHeaders.isEmpty()) {
            WebKitLinuxBridge.nativeLoadUrl(handle, url)
        } else {
            val names = additionalHttpHeaders.keys.toTypedArray()
            val values = additionalHttpHeaders.values.toTypedArray()
            WebKitLinuxBridge.nativeLoadUrlWithHeaders(handle, url, names, values)
        }
    }

    override fun loadHtml(html: String) {
        if (!isReady()) return
        WebKitLinuxBridge.nativeLoadHtml(handle, html, null)
    }

    fun loadHtml(html: String, baseUri: String?) {
        if (!isReady()) return
        WebKitLinuxBridge.nativeLoadHtml(handle, html, baseUri)
    }

    override fun goBack() {
        if (!isReady()) return
        WebKitLinuxBridge.nativeGoBack(handle)
    }

    override fun goForward() {
        if (!isReady()) return
        WebKitLinuxBridge.nativeGoForward(handle)
    }

    override fun reload() {
        if (!isReady()) return
        WebKitLinuxBridge.nativeReload(handle)
    }

    override fun stopLoading() {
        if (!isReady()) return
        WebKitLinuxBridge.nativeStopLoading(handle)
    }

    override fun evaluateJavaScript(script: String, callback: (String) -> Unit) {
        if (!isReady()) {
            callback("")
            return
        }
        WebKitLinuxBridge.registerJsCallback(handle, callback)
        WebKitLinuxBridge.nativeEvaluateJavaScript(handle, script)
    }

    override fun drainIpcMessages(): List<String> =
        if (!isReady()) emptyList() else WebKitLinuxBridge.drainIpcMessages(handle)

    override fun addNavigateListener(listener: (String) -> Boolean) {
        if (!isReady()) return
        WebKitLinuxBridge.addNavigateListener(handle, listener)
    }

    override fun removeNavigateListener(listener: (String) -> Boolean) {
        if (!isReady()) return
        WebKitLinuxBridge.removeNavigateListener(handle, listener)
    }

    override fun captureScreenshotNative(): ByteArray? {
        // Synchronous API is not available; callers should use the suspend path.
        return null
    }

    suspend fun captureScreenshotAsync(): ByteArray? {
        if (!isReady()) return null
        val deferred = CompletableDeferred<ByteArray?>()
        WebKitLinuxBridge.registerScreenshotDeferred(handle, deferred)
        WebKitLinuxBridge.nativeCaptureScreenshot(handle)
        return deferred.await()
    }

    suspend fun getCookiesJson(url: String): String {
        if (!isReady()) return "[]"
        val deferred = CompletableDeferred<String>()
        WebKitLinuxBridge.registerCookieDeferred(handle, deferred)
        WebKitLinuxBridge.nativeGetCookies(handle, url)
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
        WebKitLinuxBridge.nativeSetCookie(
            handle, name, value, domain, path, secure, httpOnly, expiresMs, sameSite,
        )
    }

    fun removeAllCookiesNative() {
        if (!isReady()) return
        WebKitLinuxBridge.nativeRemoveAllCookies(handle)
    }

    fun removeCookiesForUrlNative(url: String) {
        if (!isReady()) return
        WebKitLinuxBridge.nativeRemoveCookiesForUrl(handle, url)
    }

    fun setZoomLevel(zoom: Double) {
        if (!isReady()) return
        WebKitLinuxBridge.nativeSetZoomLevel(handle, zoom)
    }

    override fun openDevTools() {
        if (!isReady()) return
        WebKitLinuxBridge.nativeOpenDevTools(handle)
    }

    override fun closeDevTools() {
        if (!isReady()) return
        WebKitLinuxBridge.nativeCloseDevTools(handle)
    }

    override fun focus() {
        if (!isReady()) return
        WebKitLinuxBridge.nativeFocus(handle)
    }

    override fun destroy() {
        if (released) return
        released = true
        WebKitLinuxBridge.clearHandle(handle)
        WebKitLinuxBridge.nativeRelease(handle)
    }
}
