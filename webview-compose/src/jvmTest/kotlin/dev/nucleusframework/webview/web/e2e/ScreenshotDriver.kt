package dev.nucleusframework.webview.web.e2e

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import dev.nucleusframework.webview.web.WebViewState
import dev.nucleusframework.webview.web.toAwtImage
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.delay

@Composable
internal fun ScreenshotDriver(
    state: WebViewState,
    onFailure: (String) -> Unit,
    onDone: () -> Unit,
) {
    LaunchedEffect(Unit) {
        try {
            awaitWebViewReady(state)
            state.webView?.loadHtml(E2E_PAGE_HTML, "https://e2e.local/")
            awaitFinished(state, "screenshot page finished")
            delay(500)
            val bytes = state.webView?.captureScreenshotOrNull()
            assertNotNull(bytes, "screenshot bytes null")
            assertTrue(bytes.size > 100, "screenshot too small: ${bytes.size}")
            assertEquals(0x89.toByte(), bytes[0])
            assertEquals('P'.code.toByte(), bytes[1])
            assertEquals('N'.code.toByte(), bytes[2])
            assertEquals('G'.code.toByte(), bytes[3])
            val awt = state.webView?.toAwtImage()
            assertNotNull(awt, "awt image null")
            assertTrue(awt.width > 0 && awt.height > 0, "awt image empty")
            println("[e2e] screenshot OK (${bytes.size} bytes, ${awt.width}x${awt.height})")
            onDone()
        } catch (t: Throwable) {
            onFailure("screenshot: ${t.message}")
            onDone()
        }
    }
}
