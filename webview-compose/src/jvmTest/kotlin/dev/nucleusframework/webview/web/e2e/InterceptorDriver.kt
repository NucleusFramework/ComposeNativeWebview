package dev.nucleusframework.webview.web.e2e

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import dev.nucleusframework.webview.web.WebViewNavigator
import dev.nucleusframework.webview.web.WebViewState
import kotlin.test.assertTrue
import kotlinx.coroutines.delay

@Composable
internal fun InterceptorDriver(
    state: WebViewState,
    navigator: WebViewNavigator,
    rejectExample: (Boolean) -> Unit,
    onFailure: (String) -> Unit,
    onDone: () -> Unit,
) {
    LaunchedEffect(Unit) {
        try {
            awaitWebViewReady(state)
            awaitFinished(state, "interceptor page ready")
            rejectExample(true)
            delay(100)
            val urlBefore = state.lastLoadedUrl
            navigator.loadUrl("https://example.com/")
            delay(1500)
            val marker =
                runCatching {
                    awaitJsString(
                        navigator,
                        "document.getElementById('marker') ? document.getElementById('marker').textContent : 'missing'",
                    )
                }.getOrDefault("missing")
            assertTrue(
                marker.contains("hello-e2e") || marker.contains("page-"),
                "expected still on e2e page after reject, marker=$marker lastUrl=${state.lastLoadedUrl} before=$urlBefore",
            )
            rejectExample(false)
            println("[e2e] request interceptor reject OK")
            onDone()
        } catch (t: Throwable) {
            onFailure("interceptor: ${t.message}")
            onDone()
        }
    }
}
