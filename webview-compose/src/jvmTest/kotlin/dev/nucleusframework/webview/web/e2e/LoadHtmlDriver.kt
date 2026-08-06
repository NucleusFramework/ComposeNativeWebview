package dev.nucleusframework.webview.web.e2e

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import dev.nucleusframework.webview.web.WebViewNavigator
import dev.nucleusframework.webview.web.WebViewState
import dev.nucleusframework.webview.web.linux.LinuxWebKitNativeWebView
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Composable
internal fun LoadHtmlDriver(
    state: WebViewState,
    navigator: WebViewNavigator,
    onFailure: (String) -> Unit,
    onDone: () -> Unit,
) {
    LaunchedEffect(Unit) {
        try {
            awaitWebViewReady(state)
            awaitFinished(state)
            val title = awaitJsString(navigator, "document.title")
            assertTrue(title.contains("E2E"), "expected title to contain E2E, got: $title")
            val marker = awaitJsString(navigator, "document.getElementById('marker').textContent")
            assertEquals("hello-e2e", marker.trim('"'))
            val url = state.lastLoadedUrl
            assertNotNull(url, "lastLoadedUrl should be set")
            assertTrue(
                url.contains("e2e.local") || url.startsWith("data:") || url == "about:blank" ||
                    state.webView?.nativeWebView is LinuxWebKitNativeWebView,
                "unexpected url=$url",
            )
            println("[e2e] loadHtml + title + marker OK")
            onDone()
        } catch (t: Throwable) {
            onFailure("loadHtml: ${t.message}")
            onDone()
        }
    }
}
