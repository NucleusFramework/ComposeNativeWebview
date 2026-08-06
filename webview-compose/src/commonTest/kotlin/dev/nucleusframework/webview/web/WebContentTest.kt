package dev.nucleusframework.webview.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Shared multiplatform suite — must pass on JVM, Android host, iOS simulator, Wasm.
 */
class WebContentTest {
    @Test
    fun urlKeepsHeaders() {
        val content =
            WebContent.Url(
                url = "https://example.com/path",
                additionalHttpHeaders = mapOf("X-Test" to "1"),
            )
        assertIs<WebContent.Url>(content)
        assertEquals("https://example.com/path", content.url)
        assertEquals(mapOf("X-Test" to "1"), content.additionalHttpHeaders)
    }

    @Test
    fun dataDefaults() {
        val content = WebContent.Data(data = "<html></html>")
        assertIs<WebContent.Data>(content)
        assertEquals("<html></html>", content.data)
        assertNull(content.baseUrl)
        assertEquals("utf-8", content.encoding)
        assertNull(content.mimeType)
        assertNull(content.historyUrl)
    }

    @Test
    fun fileHoldsReadType() {
        val content =
            WebContent.File(
                fileName = "fixture.html",
                readType = WebViewFileReadType.COMPOSE_RESOURCE_FILES,
            )
        assertIs<WebContent.File>(content)
        assertEquals("fixture.html", content.fileName)
        assertEquals(WebViewFileReadType.COMPOSE_RESOURCE_FILES, content.readType)
    }

    @Test
    fun navigatorOnlyIsSingletonStyle() {
        assertIs<WebContent.NavigatorOnly>(WebContent.NavigatorOnly)
    }
}
