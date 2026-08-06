package dev.nucleusframework.webview.demo.visualsuite

import dev.nucleusframework.webview.web.LoadingState
import dev.nucleusframework.webview.web.NativeWebView
import dev.nucleusframework.webview.web.WebViewNavigator
import dev.nucleusframework.webview.web.WebViewState
import dev.nucleusframework.webview.web.linux.LinuxWebKitNativeWebView
import dev.nucleusframework.webview.web.macos.MacOsWebKitNativeWebView
import dev.nucleusframework.webview.web.windows.WindowsWebView2NativeWebView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

internal suspend fun awaitFinished(
    state: WebViewState,
    timeoutMs: Long = 20_000,
) {
    withTimeout(timeoutMs) {
        while (state.loadingState !is LoadingState.Finished) {
            delay(40)
        }
    }
}

internal suspend fun awaitUntil(
    timeoutMs: Long = 15_000,
    description: String = "condition",
    predicate: suspend () -> Boolean,
) {
    withTimeout(timeoutMs) {
        while (!predicate()) {
            delay(40)
        }
    }
}

internal suspend fun evalJs(
    navigator: WebViewNavigator,
    script: String,
    timeoutMs: Long = 12_000,
): String {
    val deferred = CompletableDeferred<String>()
    navigator.evaluateJavaScript(script) { deferred.complete(it) }
    return withTimeout(timeoutMs) { deferred.await() }
}

/** Strip JSON string quotes from evaluateJavaScript results when present. */
internal fun unquoteJs(result: String): String {
    val t = result.trim()
    return if (t.length >= 2 && t.startsWith('"') && t.endsWith('"')) {
        t.substring(1, t.length - 1)
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\\\", "\\")
    } else {
        t
    }
}

internal suspend fun evalJsUnquoted(
    navigator: WebViewNavigator,
    script: String,
    timeoutMs: Long = 12_000,
): String = unquoteJs(evalJs(navigator, script, timeoutMs))

internal fun NativeWebView?.isLiveDesktopBackend(): Boolean =
    this != null &&
        isReady() &&
        (
            this is LinuxWebKitNativeWebView ||
                this is MacOsWebKitNativeWebView ||
                this is WindowsWebView2NativeWebView
            )

internal suspend fun waitWebView(
    state: WebViewState,
    timeoutMs: Long = 20_000,
) {
    try {
        withTimeout(timeoutMs) {
            // Windows recreates the native view once LocalTaoWindow HWND is ready —
            // wait for a live backend, not just a non-null DesktopWebView shell.
            while (!state.webView?.nativeWebView.isLiveDesktopBackend()) delay(40)
        }
    } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
        val nv = state.webView?.nativeWebView
        error(
            "Desktop WebView native backend not ready after ${timeoutMs}ms " +
                "(webView=${state.webView != null}, native=${nv?.let { it::class.simpleName }}, " +
                "ready=${nv?.isReady()}). " +
                "Build host natives first: " +
                "Linux `./gradlew :webview-compose:buildNativeLinux`, " +
                "macOS `./gradlew :webview-compose:buildNativeMacos`, " +
                "Windows `./gradlew :webview-compose:buildNativeWindows` " +
                "(MSVC + JAVA_HOME + WebView2 Runtime).",
        )
    }
}

internal fun assertThat(
    condition: Boolean,
    message: String,
) {
    if (!condition) error(message)
}

internal suspend fun <T> runCase(
    onStatus: (CaseStatus, String) -> Unit,
    block: suspend () -> T,
): T? {
    onStatus(CaseStatus.Running, "")
    return try {
        val result = block()
        onStatus(CaseStatus.Passed, "ok")
        result
    } catch (t: Throwable) {
        onStatus(CaseStatus.Failed, t.message ?: t::class.simpleName ?: "error")
        null
    }
}

internal suspend fun softTimeout(
    timeoutMs: Long,
    block: suspend () -> Unit,
): Boolean = withTimeoutOrNull(timeoutMs) { block(); true } == true

internal fun dataHtmlUrl(html: String): String {
    val b64 = java.util.Base64.getEncoder().encodeToString(html.toByteArray(Charsets.UTF_8))
    return "data:text/html;base64,$b64"
}

/**
 * Load HTML and wait until the page marker matches [expectedMarker].
 * Do not rely on LoadingState alone — it can already be Finished from the previous case.
 */
internal suspend fun loadHtmlAwaitMarker(
    navigator: WebViewNavigator,
    expectedMarker: String,
    html: String = pageWithMarker(expectedMarker),
    baseUrl: String = "https://suite.local/${expectedMarker}",
    timeoutMs: Long = 15_000,
) {
    navigator.loadHtml(html, baseUrl = baseUrl)
    awaitUntil(timeoutMs, "marker=$expectedMarker") {
        runCatching {
            evalJsUnquoted(navigator, "document.getElementById('marker')?.textContent || ''")
        }.getOrDefault("") == expectedMarker
    }
}

internal suspend fun loadUrlAwaitMarker(
    navigator: WebViewNavigator,
    expectedMarker: String,
    url: String,
    timeoutMs: Long = 15_000,
) {
    navigator.loadUrl(url)
    awaitUntil(timeoutMs, "url marker=$expectedMarker") {
        runCatching {
            evalJsUnquoted(navigator, "document.getElementById('marker')?.textContent || ''")
        }.getOrDefault("") == expectedMarker
    }
}

internal suspend fun markerOf(navigator: WebViewNavigator): String =
    runCatching {
        evalJsUnquoted(navigator, "document.getElementById('marker')?.textContent || ''")
    }.getOrDefault("")
