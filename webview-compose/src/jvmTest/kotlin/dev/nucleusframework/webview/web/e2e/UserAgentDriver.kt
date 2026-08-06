package dev.nucleusframework.webview.web.e2e

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import dev.nucleusframework.webview.web.WebView
import dev.nucleusframework.webview.web.rememberWebViewNavigator
import dev.nucleusframework.webview.web.rememberWebViewState
import kotlin.test.assertTrue
import kotlinx.coroutines.delay

@Composable
internal fun UserAgentDriver(
    onFailure: (String) -> Unit,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val navigator = rememberWebViewNavigator(coroutineScope = scope)
    val state =
        rememberWebViewState("about:blank") {
            customUserAgentString = "ComposeNativeWebView-E2E/1.0"
        }

    WebView(state = state, navigator = navigator, modifier = Modifier.fillMaxSize())

    LaunchedEffect(Unit) {
        try {
            awaitWebViewReady(state)
            navigator.loadHtml(
                html =
                    """
                    <!DOCTYPE html><html><head><title>UA</title></head>
                    <body><pre id="ua"></pre>
                    <script>document.getElementById('ua').textContent = navigator.userAgent;</script>
                    </body></html>
                    """.trimIndent(),
                baseUrl = "https://e2e.local/ua",
            )
            awaitFinished(state, "ua page finished")
            delay(200)
            val ua = awaitJsString(navigator, "navigator.userAgent")
            assertTrue(ua.contains("ComposeNativeWebView-E2E/1.0"), "custom UA not applied: $ua")
            println("[e2e] custom user-agent OK")
            onDone()
        } catch (t: Throwable) {
            onFailure("userAgent: ${t.message}")
            onDone()
        }
    }
}
