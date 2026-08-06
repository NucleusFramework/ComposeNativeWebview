package dev.nucleusframework.webview.web.e2e

import dev.nucleusframework.webview.web.LoadingState
import dev.nucleusframework.webview.web.WebViewNavigator
import dev.nucleusframework.webview.web.WebViewState
import dev.nucleusframework.webview.web.linux.LinuxWebKitNativeWebView
import dev.nucleusframework.webview.web.windows.WindowsWebView2NativeWebView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

internal fun isLiveDesktopWebView(state: WebViewState): Boolean {
    val nv = state.webView?.nativeWebView ?: return false
    return nv.isReady() &&
        (nv is LinuxWebKitNativeWebView || nv is WindowsWebView2NativeWebView)
}

internal suspend fun awaitWebViewReady(state: WebViewState, timeoutMs: Long = 15_000) {
    awaitUntil("webview attached", timeoutMs) {
        isLiveDesktopWebView(state)
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
