package dev.nucleusframework.webview.e2e.visualsuite

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.nucleusframework.webview.web.IWebView
import dev.nucleusframework.webview.web.WebViewState
import dev.nucleusframework.webview.web.linux.LinuxWebKitNativeWebView
import dev.nucleusframework.webview.web.macos.MacOsWebKitNativeWebView
import dev.nucleusframework.webview.web.toAwtImage
import dev.nucleusframework.webview.web.windows.WindowsWebView2NativeWebView
import dev.nucleusframework.window.tao.LocalTaoWindow
import java.io.File
import java.util.Locale
import kotlinx.coroutines.delay

actual fun suiteCapabilities(): Set<SuiteCapability> =
    setOf(
        SuiteCapability.HistoryNavigation,
        SuiteCapability.DataUrlNavigation,
        SuiteCapability.CookieDomainApi,
        SuiteCapability.ScreenshotPng,
        SuiteCapability.ScreenshotPixels,
        SuiteCapability.IsolatedNativeWebView,
        SuiteCapability.DesktopNativeControls,
        SuiteCapability.DocumentStartJsBridge,
    )

actual fun isPlatformWebViewReady(state: WebViewState): Boolean {
    val nv = state.webView?.nativeWebView ?: return false
    return nv.isReady() &&
        (
            nv is LinuxWebKitNativeWebView ||
                nv is MacOsWebKitNativeWebView ||
                nv is WindowsWebView2NativeWebView
            )
}

@Composable
actual fun rememberSuiteParentHandle(): Long =
    LocalTaoWindow.current?.nativeHandle ?: 0L

actual suspend fun withIsolatedNativeWebView(
    parentHandle: Long,
    customUserAgent: String?,
    initScript: String?,
    incognito: Boolean,
    dataDirectory: String?,
    enableDevtools: Boolean,
    block: suspend (IsolatedNativeWebView) -> Unit,
) {
    val os = System.getProperty("os.name", "").lowercase(Locale.ENGLISH)
    val isWin = os.contains("win")
    val isLinux = os.contains("nux") || os.contains("nix") || os.contains("aix")
    val isMac = os.contains("mac")

    val native =
        when {
            isLinux ->
                LinuxWebKitNativeWebView(
                    customUserAgent = customUserAgent,
                    dataDirectory = dataDirectory,
                    initScript = initScript,
                    incognito = incognito,
                    enableDevtools = enableDevtools,
                    javascriptEnabled = true,
                    zoomLevel = 1.0,
                    transparent = false,
                    backgroundColor = Color.White,
                )
            isMac ->
                MacOsWebKitNativeWebView(
                    customUserAgent = customUserAgent,
                    dataDirectory = dataDirectory,
                    initScript = initScript,
                    incognito = incognito,
                    enableDevtools = enableDevtools,
                    javascriptEnabled = true,
                    zoomLevel = 1.0,
                    transparent = false,
                    backgroundColor = Color.White,
                )
            isWin -> {
                require(parentHandle != 0L) { "parent HWND required for isolated Windows WebView2" }
                WindowsWebView2NativeWebView(
                    parentHwnd = parentHandle,
                    customUserAgent = customUserAgent,
                    dataDirectory = dataDirectory,
                    initScript = initScript,
                    incognito = incognito,
                    enableDevtools = enableDevtools,
                    javascriptEnabled = true,
                    zoomLevel = 1.0,
                    transparent = false,
                    backgroundColor = Color.White,
                )
            }
            else -> error("isolated desktop WebView unsupported on $os")
        }

    val isolated = DesktopIsolatedNativeWebView(native)
    try {
        if (isWin) delay(200)
        assertThat(isolated.isReady(), "isolated native not ready")
        block(isolated)
    } finally {
        isolated.destroy()
    }
}

private class DesktopIsolatedNativeWebView(
    private val native: dev.nucleusframework.webview.web.NativeWebView,
) : IsolatedNativeWebView {
    override fun isReady(): Boolean = native.isReady()

    override fun loadHtml(
        html: String,
        baseUri: String?,
    ) {
        when (native) {
            is LinuxWebKitNativeWebView -> native.loadHtml(html, baseUri)
            is MacOsWebKitNativeWebView -> native.loadHtml(html, baseUri)
            is WindowsWebView2NativeWebView -> native.loadHtml(html, baseUri)
            else -> native.loadHtml(html)
        }
    }

    override fun evaluateJavaScript(
        script: String,
        callback: (String) -> Unit,
    ) {
        native.evaluateJavaScript(script, callback)
    }

    override fun setCookieNative(
        name: String,
        value: String,
        domain: String,
        path: String,
        secure: Boolean,
        httpOnly: Boolean,
        expiresMs: Long,
        sameSite: String,
    ) {
        when (native) {
            is LinuxWebKitNativeWebView ->
                native.setCookieNative(
                    name, value, domain, path, secure, httpOnly, expiresMs, sameSite,
                )
            is MacOsWebKitNativeWebView ->
                native.setCookieNative(
                    name, value, domain, path, secure, httpOnly, expiresMs, sameSite,
                )
            is WindowsWebView2NativeWebView ->
                native.setCookieNative(
                    name, value, domain, path, secure, httpOnly, expiresMs, sameSite,
                )
            else -> error("setCookieNative unsupported on ${native::class.simpleName}")
        }
    }

    override fun setZoomLevel(level: Double) {
        when (native) {
            is LinuxWebKitNativeWebView -> native.setZoomLevel(level)
            is MacOsWebKitNativeWebView -> native.setZoomLevel(level)
            is WindowsWebView2NativeWebView -> native.setZoomLevel(level)
            else -> Unit
        }
    }

    override fun focus() {
        native.focus()
    }

    override fun openDevTools() {
        native.openDevTools()
    }

    override fun closeDevTools() {
        native.closeDevTools()
    }

    override fun destroy() {
        native.destroy()
    }
}

actual suspend fun decodeScreenshotPixels(webView: IWebView?): ScreenshotPixels? {
    val img = webView?.toAwtImage() ?: return null
    val w = img.width
    val h = img.height
    if (w <= 0 || h <= 0) return null
    val samples =
        listOf(
            img.getRGB(w / 2, h / 2),
            img.getRGB(w / 3, h / 3),
            img.getRGB(2 * w / 3, 2 * h / 3),
        )
    return ScreenshotPixels(width = w, height = h, samples = samples)
}

actual fun writeSuiteReport(
    report: SuiteReport,
    preferredPath: String?,
): String {
    val path =
        preferredPath
            ?: System.getenv("COMPOSEWEBVIEW_SUITE_REPORT")
            ?: run {
                val dir = System.getProperty("java.io.tmpdir")?.trimEnd('/', '\\') ?: "."
                "$dir${File.separator}composewebview-visual-suite-report.txt"
            }
    val body = formatSuiteReport(report)
    File(path).apply {
        parentFile?.mkdirs()
        writeText(body)
    }
    println(body)
    return path
}

actual fun currentTimeNanos(): Long = System.nanoTime()

actual fun createTempProfileDirectory(prefix: String): String {
    val dir =
        File.createTempFile(prefix, null).apply {
            delete()
            mkdirs()
            deleteOnExit()
        }
    return dir.absolutePath
}
