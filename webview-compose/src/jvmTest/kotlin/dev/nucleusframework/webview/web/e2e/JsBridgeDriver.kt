package dev.nucleusframework.webview.web.e2e

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import dev.nucleusframework.webview.web.WebViewNavigator
import dev.nucleusframework.webview.web.WebViewState
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertTrue
import kotlinx.coroutines.delay

@Composable
internal fun JsBridgeDriver(
    state: WebViewState,
    navigator: WebViewNavigator,
    bridgeHits: CopyOnWriteArrayList<String>,
    onFailure: (String) -> Unit,
    onDone: () -> Unit,
) {
    LaunchedEffect(Unit) {
        try {
            awaitWebViewReady(state)
            navigator.loadHtml(E2E_PAGE_HTML, baseUrl = "https://e2e.local/")
            awaitFinished(state, "bridge page finished")
            delay(400)
            bridgeHits.clear()
            navigator.evaluateJavaScript(
                """
                window.kmpJsBridge.callNative('e2eEcho', JSON.stringify({ping:1}), function(r){});
                """.trimIndent(),
            )
            awaitUntil("js bridge message received") { bridgeHits.isNotEmpty() }
            assertTrue(bridgeHits.first().contains("ping"), "unexpected bridge payload: $bridgeHits")
            println("[e2e] JS bridge OK")
            onDone()
        } catch (t: Throwable) {
            onFailure("jsBridge: ${t.message}")
            onDone()
        }
    }
}
