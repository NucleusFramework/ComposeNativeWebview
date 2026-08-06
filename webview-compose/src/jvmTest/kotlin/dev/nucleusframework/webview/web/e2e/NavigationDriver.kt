package dev.nucleusframework.webview.web.e2e

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import dev.nucleusframework.webview.web.LoadingState
import dev.nucleusframework.webview.web.WebViewNavigator
import dev.nucleusframework.webview.web.WebViewState

@Composable
internal fun NavigationDriver(
    state: WebViewState,
    navigator: WebViewNavigator,
    onFailure: (String) -> Unit,
    onDone: () -> Unit,
) {
    LaunchedEffect(Unit) {
        try {
            awaitWebViewReady(state)
            navigator.loadHtml(E2E_PAGE_A, baseUrl = "https://e2e.local/a")
            awaitUntil("page A loaded") {
                state.loadingState is LoadingState.Finished &&
                    awaitJsString(navigator, "document.getElementById('marker').textContent")
                        .contains("page-a")
            }
            navigator.loadHtml(E2E_PAGE_B, baseUrl = "https://e2e.local/b")
            awaitUntil("page B loaded") {
                state.loadingState is LoadingState.Finished &&
                    awaitJsString(navigator, "document.getElementById('marker').textContent")
                        .contains("page-b")
            }
            awaitUntil("canGoBack after B") { navigator.canGoBack }
            navigator.navigateBack()
            awaitUntil("back to A") {
                awaitJsString(navigator, "document.getElementById('marker').textContent")
                    .contains("page-a")
            }
            awaitUntil("canGoForward after back") { navigator.canGoForward }
            navigator.navigateForward()
            awaitUntil("forward to B") {
                awaitJsString(navigator, "document.getElementById('marker').textContent")
                    .contains("page-b")
            }
            navigator.reload()
            awaitFinished(state, "reload finished")
            println("[e2e] navigation back/forward/reload OK")
            onDone()
        } catch (t: Throwable) {
            onFailure("navigation: ${t.message}")
            onDone()
        }
    }
}
