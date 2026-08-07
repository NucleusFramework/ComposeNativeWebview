package dev.nucleusframework.webview.e2e.visualsuite

import androidx.compose.runtime.Composable
import dev.nucleusframework.webview.web.IWebView
import dev.nucleusframework.webview.web.WebViewState
import kotlinx.browser.window

actual fun suiteCapabilities(): Set<SuiteCapability> =
    // Wasm IFrame limits: no history, no data: JS access, host-only cookies,
    // screenshot needs optional html2canvas (not bundled).
    emptySet()

actual fun isPlatformWebViewReady(state: WebViewState): Boolean = state.webView != null

@Composable
actual fun rememberSuiteParentHandle(): Long = 0L

actual suspend fun withIsolatedNativeWebView(
    parentHandle: Long,
    customUserAgent: String?,
    initScript: String?,
    incognito: Boolean,
    dataDirectory: String?,
    enableDevtools: Boolean,
    block: suspend (IsolatedNativeWebView) -> Unit,
) {
    error("IsolatedNativeWebView not available on Wasm")
}

actual suspend fun decodeScreenshotPixels(webView: IWebView?): ScreenshotPixels? = null

actual fun writeSuiteReport(
    report: SuiteReport,
    preferredPath: String?,
): String {
    val body = formatSuiteReport(report)
    println(body)
    runCatching {
        window.localStorage.setItem("composewebview-visual-suite-report", body)
    }
    return preferredPath ?: "browser:localStorage:composewebview-visual-suite-report"
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun jsNowMs(): Double = js("Date.now()")

actual fun currentTimeNanos(): Long = (jsNowMs() * 1_000_000.0).toLong()

actual fun createTempProfileDirectory(prefix: String): String = "wasm-profile-$prefix"
