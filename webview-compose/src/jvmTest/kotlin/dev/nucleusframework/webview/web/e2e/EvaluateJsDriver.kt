package dev.nucleusframework.webview.web.e2e

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import dev.nucleusframework.webview.web.WebViewNavigator
import dev.nucleusframework.webview.web.WebViewState
import kotlin.test.assertTrue

@Composable
internal fun EvaluateJsDriver(
    state: WebViewState,
    navigator: WebViewNavigator,
    onFailure: (String) -> Unit,
    onDone: () -> Unit,
) {
    LaunchedEffect(Unit) {
        try {
            awaitWebViewReady(state)
            awaitJsString(navigator, "window.__e2e = 41; window.__e2e + 1")
            val value = awaitJsString(navigator, "window.__e2e")
            assertTrue(value.contains("41"), "expected 41, got $value")
            val outer = awaitJsString(navigator, "document.documentElement.outerHTML")
            assertTrue(outer.contains("E2E"), "outerHTML missing E2E: $outer")
            println("[e2e] evaluateJavaScript OK")
            onDone()
        } catch (t: Throwable) {
            onFailure("evaluateJs: ${t.message}")
            onDone()
        }
    }
}
