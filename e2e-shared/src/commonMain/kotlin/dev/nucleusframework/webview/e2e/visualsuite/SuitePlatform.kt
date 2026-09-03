package dev.nucleusframework.webview.e2e.visualsuite

import androidx.compose.runtime.Composable
import dev.nucleusframework.webview.web.IWebView
import dev.nucleusframework.webview.web.WebViewState

/**
 * Platform features used by the shared e2e catalog.
 * Cases that need a missing capability are **Skipped** (not Failed)
 * so the same catalog runs everywhere with an honest matrix.
 */
enum class SuiteCapability {
    /** Real back/forward history (not available on Wasm iframe). */
    HistoryNavigation,

    /**
     * `data:text/html` navigations with same-origin JS access.
     * Wasm IFrame treats data: as opaque / blocked in many browsers.
     */
    DataUrlNavigation,

    /**
     * Cookie jar that honors domain/path for arbitrary URLs (not browser
     * document.cookie restricted to the host page origin).
     */
    CookieDomainApi,

    /** PNG capture via [IWebView.captureScreenshotOrNull]. */
    ScreenshotPng,

    /** Pixel sampling of screenshots (desktop AWT path today). */
    ScreenshotPixels,

    /** Isolated native WebView with UA / initScript / data dir / incognito. */
    IsolatedNativeWebView,

    /** Native isReady / focus / zoom / devtools (desktop JNI backends). */
    DesktopNativeControls,

    /**
     * JS bridge installed as a native user script at document start, so page
     * scripts can call it while the document is still parsing. Desktop only:
     * Android / iOS / WasmJs still inject it after load.
     */
    DocumentStartJsBridge,
}

expect fun suiteCapabilities(): Set<SuiteCapability>

/** True once the platform WebView is usable for e2e assertions. */
expect fun isPlatformWebViewReady(state: WebViewState): Boolean

/** Parent HWND for Windows isolated WebView2 (0 elsewhere). */
@Composable
expect fun rememberSuiteParentHandle(): Long

/**
 * Runs [block] with a throwaway native WebView configured at construction time.
 * Only available when [SuiteCapability.IsolatedNativeWebView] is present.
 */
expect suspend fun withIsolatedNativeWebView(
    parentHandle: Long,
    customUserAgent: String? = null,
    initScript: String? = null,
    incognito: Boolean = false,
    dataDirectory: String? = null,
    enableDevtools: Boolean = false,
    block: suspend (IsolatedNativeWebView) -> Unit,
)

/**
 * Minimal surface used by isolated construction-time tests (UA, initScript, cookies).
 */
interface IsolatedNativeWebView {
    fun isReady(): Boolean

    fun loadHtml(
        html: String,
        baseUri: String? = null,
    )

    fun evaluateJavaScript(
        script: String,
        callback: (String) -> Unit = {},
    )

    fun setCookieNative(
        name: String,
        value: String,
        domain: String,
        path: String,
        secure: Boolean,
        httpOnly: Boolean,
        expiresMs: Long,
        sameSite: String,
    )

    fun setZoomLevel(level: Double) {}

    fun focus() {}

    fun openDevTools() {}

    fun closeDevTools() {}

    fun destroy()
}

/** Decode screenshot bytes into width/height + RGB samples when supported. */
expect suspend fun decodeScreenshotPixels(webView: IWebView?): ScreenshotPixels?

data class ScreenshotPixels(
    val width: Int,
    val height: Int,
    val samples: List<Int>,
)

/** Write report to disk/console; returns a path or logical handle for the host. */
expect fun writeSuiteReport(
    report: SuiteReport,
    preferredPath: String? = null,
): String

expect fun currentTimeNanos(): Long

expect fun createTempProfileDirectory(prefix: String): String
