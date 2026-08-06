package dev.nucleusframework.webview.web.e2e

import dev.nucleusframework.webview.web.LoadingState
import dev.nucleusframework.webview.web.WebViewNavigator
import dev.nucleusframework.webview.web.WebViewState
import dev.nucleusframework.webview.web.linux.LinuxWebKitNativeWebView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

internal suspend fun awaitWebViewReady(state: WebViewState, timeoutMs: Long = 15_000) {
    awaitUntil("webview attached", timeoutMs) {
        val nv = state.webView?.nativeWebView
        nv is LinuxWebKitNativeWebView && nv.isReady()
    }
}

internal suspend fun awaitUntil(
    description: String,
    timeoutMs: Long = 15_000,
    predicate: suspend () -> Boolean,
) {
    withTimeout(timeoutMs) {
        while (!predicate()) {
            delay(50)
        }
    }
}

internal suspend fun awaitJsString(
    navigator: WebViewNavigator,
    script: String,
    timeoutMs: Long = 10_000,
): String {
    val deferred = CompletableDeferred<String>()
    navigator.evaluateJavaScript(script) { result ->
        deferred.complete(result)
    }
    return withTimeout(timeoutMs) { deferred.await() }
}

internal suspend fun awaitFinished(state: WebViewState, label: String = "page finished") {
    awaitUntil(label) { state.loadingState is LoadingState.Finished }
}
