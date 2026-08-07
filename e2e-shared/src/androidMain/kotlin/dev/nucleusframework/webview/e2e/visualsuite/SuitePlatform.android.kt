package dev.nucleusframework.webview.e2e.visualsuite

import android.util.Log
import androidx.compose.runtime.Composable
import dev.nucleusframework.webview.web.IWebView
import dev.nucleusframework.webview.web.WebViewState
import java.io.File

actual fun suiteCapabilities(): Set<SuiteCapability> =
    setOf(
        SuiteCapability.HistoryNavigation,
        SuiteCapability.DataUrlNavigation,
        SuiteCapability.CookieDomainApi,
        SuiteCapability.ScreenshotPng,
        // Pixel sampling of Bitmaps can be added later; PNG magic still runs.
    )

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
    error("IsolatedNativeWebView not available on Android")
}

actual suspend fun decodeScreenshotPixels(webView: IWebView?): ScreenshotPixels? = null

actual fun writeSuiteReport(
    report: SuiteReport,
    preferredPath: String?,
): String {
    val path =
        preferredPath
            ?: System.getenv("COMPOSEWEBVIEW_SUITE_REPORT")
            ?: File(
                System.getProperty("java.io.tmpdir") ?: ".",
                "composewebview-visual-suite-report.txt",
            ).absolutePath
    val body = formatSuiteReport(report)
    File(path).apply {
        parentFile?.mkdirs()
        writeText(body)
    }
    // Logcat-friendly dump (CI greps SUITE_FINISHED from MainActivity + report lines).
    Log.i("ComposeWebViewE2E", "REPORT_PATH=$path")
    body.lineSequence().forEach { line ->
        Log.i("ComposeWebViewE2E", line)
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
