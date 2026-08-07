package dev.nucleusframework.webview.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Shared multiplatform suite — must pass on JVM, Android host, iOS simulator, Wasm.
 */
class WebViewStateTest {
    @Test
    fun startsInitializingAndReportsLoading() {
        val state = WebViewState(WebContent.Url("https://example.com"))
        assertIs<LoadingState.Initializing>(state.loadingState)
        assertTrue(state.isLoading)
        assertNull(state.lastLoadedUrl)
        assertNull(state.pageTitle)
        assertNull(state.webView)
    }

    @Test
    fun isLoadingFalseWhenFinished() {
        val state = WebViewState(WebContent.Url("https://example.com"))
        state.loadingState = LoadingState.Finished
        assertFalse(state.isLoading)
    }

    @Test
    fun isLoadingTrueWhileLoading() {
        val state = WebViewState(WebContent.Url("https://example.com"))
        state.loadingState = LoadingState.Loading(0.5f)
        assertTrue(state.isLoading)
    }

    @Test
    fun contentCanBeReplaced() {
        val state = WebViewState(WebContent.Url("https://a.example"))
        state.content = WebContent.Data("<p>hi</p>", baseUrl = "https://b.example")
        val data = assertIs<WebContent.Data>(state.content)
        assertEquals("<p>hi</p>", data.data)
        assertEquals("https://b.example", data.baseUrl)
    }

    @Test
    fun errorsListIsMutable() {
        val state = WebViewState(WebContent.Url("https://example.com"))
        assertTrue(state.errorsForCurrentRequest.isEmpty())
        state.errorsForCurrentRequest.add(
            WebViewError(code = 404, description = "not found", isFromMainFrame = true),
        )
        assertEquals(1, state.errorsForCurrentRequest.size)
        assertEquals(404, state.errorsForCurrentRequest.first().code)
    }

    @Test
    fun webSettingsDefaultsAreSensible() {
        val state = WebViewState(WebContent.Url("https://example.com"))
        assertTrue(state.webSettings.isJavaScriptEnabled)
        assertTrue(state.webSettings.supportZoom)
        assertEquals(1.0, state.webSettings.zoomLevel)
        assertNull(state.webSettings.customUserAgentString)
    }
}
