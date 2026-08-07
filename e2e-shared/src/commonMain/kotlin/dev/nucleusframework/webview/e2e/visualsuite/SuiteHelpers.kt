package dev.nucleusframework.webview.e2e.visualsuite

import dev.nucleusframework.webview.web.LoadingState
import dev.nucleusframework.webview.web.WebViewNavigator
import dev.nucleusframework.webview.web.WebViewState
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
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

internal suspend fun waitWebView(
    state: WebViewState,
    timeoutMs: Long = 20_000,
) {
    try {
        withTimeout(timeoutMs) {
            while (!isPlatformWebViewReady(state)) delay(40)
        }
    } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
        error(
            "WebView not ready after ${timeoutMs}ms " +
                "(webView=${state.webView != null}, platform=${suiteCapabilities()}). " +
                "Desktop: build natives first " +
                "(`:webview-compose:buildNativeLinux` / Macos / Windows).",
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
): Boolean = withTimeoutOrNull(timeoutMs) {
    block()
    true
} == true

@OptIn(ExperimentalEncodingApi::class)
internal fun dataHtmlUrl(html: String): String {
    val b64 = Base64.encode(html.encodeToByteArray())
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

internal suspend fun IsolatedNativeWebView.evalJsAsync(
    script: String,
    timeoutMs: Long = 12_000,
): String {
    val deferred = CompletableDeferred<String>()
    evaluateJavaScript(script) { deferred.complete(it) }
    return withTimeout(timeoutMs) { deferred.await() }
}

internal suspend fun IsolatedNativeWebView.evalJsUnquotedAsync(
    script: String,
    timeoutMs: Long = 12_000,
): String = unquoteJs(evalJsAsync(script, timeoutMs))

internal suspend fun IsolatedNativeWebView.loadHtmlAwaitMarker(
    expectedMarker: String,
    html: String = pageWithMarker(expectedMarker),
    baseUri: String? = "https://suite.local/$expectedMarker",
    timeoutMs: Long = 15_000,
) {
    loadHtml(html, baseUri)
    awaitUntil(timeoutMs, "isolated marker=$expectedMarker") {
        runCatching {
            evalJsUnquotedAsync("document.getElementById('marker')?.textContent || ''")
        }.getOrDefault("") == expectedMarker
    }
}

internal class IntCounter {
    private var value = 0

    fun get(): Int = value

    fun incrementAndGet(): Int {
        value += 1
        return value
    }
}
