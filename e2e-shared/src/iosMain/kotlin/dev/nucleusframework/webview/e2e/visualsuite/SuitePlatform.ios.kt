package dev.nucleusframework.webview.e2e.visualsuite

import androidx.compose.runtime.Composable
import dev.nucleusframework.webview.web.IWebView
import dev.nucleusframework.webview.web.WebViewState
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.writeToFile
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.posix.mkdir

actual fun suiteCapabilities(): Set<SuiteCapability> =
    setOf(
        SuiteCapability.HistoryNavigation,
        SuiteCapability.DataUrlNavigation,
        SuiteCapability.CookieDomainApi,
        SuiteCapability.ScreenshotPng,
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
    error("IsolatedNativeWebView not available on iOS")
}

actual suspend fun decodeScreenshotPixels(webView: IWebView?): ScreenshotPixels? = null

@OptIn(ExperimentalForeignApi::class)
actual fun writeSuiteReport(
    report: SuiteReport,
    preferredPath: String?,
): String {
    val body = formatSuiteReport(report)
    val path =
        preferredPath
            ?: (NSTemporaryDirectory() + "composewebview-visual-suite-report.txt")
    (body as NSString).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
    println(body)
    return path
}

// Prefer kotlin.system — NSDate.timeIntervalSince1970 is missing under Xcode 26 SDK bindings.
actual fun currentTimeNanos(): Long = kotlin.system.getTimeNanos()

@OptIn(ExperimentalForeignApi::class)
actual fun createTempProfileDirectory(prefix: String): String {
    val path = NSTemporaryDirectory() + prefix + NSUUID().UUIDString()
    mkdir(path, 448u) // 0700
    return path
}
