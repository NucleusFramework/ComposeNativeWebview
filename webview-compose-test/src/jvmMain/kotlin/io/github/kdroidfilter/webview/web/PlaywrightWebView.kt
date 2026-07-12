package io.github.kdroidfilter.webview.web

import com.microsoft.playwright.Playwright
import io.github.kdroidfilter.webview.wry.Rgba
import io.github.kdroidfilter.webview.wry.WryWebViewPanel
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

const val PLAYWRIGHT_PAGE_WIDTH = 1024
const val PLAYWRIGHT_PAGE_HEIGHT = 720

/**
 * A mock implementation of [WryWebViewPanel] that uses Playwright for some operations.
 * This is useful for testing without a real native WebView.
 */
class PlaywrightWebView(param: WebViewFactoryParam) : WryWebViewPanel(
    initialUrl = (param.state.content as? WebContent.Url)?.url ?: "about:blank",
    backgroundColor = Rgba(0u.toUByte(), 0u.toUByte(), 0u.toUByte(), 0u.toUByte())
) {
    val evaluatedScripts = mutableListOf<String>()
    var currentContent: String = (param.state.content as? WebContent.Url)?.url ?: "about:blank"

    override fun evaluateJavaScript(script: String, callback: (String) -> Unit) {
        evaluatedScripts.add(script)
        if (script == "document.documentElement.outerHTML") {
            if (currentContent.startsWith("http")) {
                runCatching {
                    Playwright.create().use { playwright ->
                        playwright.chromium().launch().use { browser ->
                            browser.newPage().use { page ->
                                page.navigate(currentContent)
                                val html = page.content()
                                callback(html)
                            }
                        }
                    }
                }.onFailure {
                    callback("<html><body>Playwright failed: ${it.message}</body></html>")
                }
            } else {
                callback("<html><body>Mock Content: $currentContent</body></html>")
            }
        } else {
            callback("true")
        }
    }

    override fun isReady(): Boolean = true
    override fun isLoading(): Boolean = false
    override fun getCurrentUrl(): String = currentContent
    override fun getTitle(): String = "Playwright WebView"

    override fun loadUrl(url: String, additionalHttpHeaders: Map<String, String>) {
        currentContent = url
    }

    override fun loadHtml(html: String) {
        currentContent = "HTML content"
    }

    override fun stopLoading() {}
    override fun reload() {}
    override fun goBack() {}
    override fun goForward() {}

    override fun captureScreenshot(nativeBytes: ByteArray?): BufferedImage {
        // Try to use Playwright for a real screenshot if it's a URL
        if (currentContent.startsWith("http")) {
            runCatching {
                Playwright.create().use { playwright ->
                    playwright.chromium().launch().use { browser ->
                        browser.newPage().use { page ->
                            page.setViewportSize(PLAYWRIGHT_PAGE_WIDTH, PLAYWRIGHT_PAGE_HEIGHT)
                            page.navigate(currentContent)
                            val bytes = page.screenshot()
                            return ImageIO.read(ByteArrayInputStream(bytes))
                        }
                    }
                }
            }.onFailure {
                println("Playwright failed: ${it.message}. Falling back to mock.")
            }
        }

        val img = BufferedImage(PLAYWRIGHT_PAGE_WIDTH, PLAYWRIGHT_PAGE_HEIGHT, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        // Fill background with a recognizable color (e.g., Light Gray)
        g.color = Color.LIGHT_GRAY
        g.fillRect(0, 0, PLAYWRIGHT_PAGE_WIDTH, PLAYWRIGHT_PAGE_HEIGHT)
        // Draw some "content"
        g.color = Color.BLACK
        g.drawString("Mock: $currentContent", 5, 50)
        g.dispose()
        return img
    }
}

/**
 * A factory function that creates a [PlaywrightWebView].
 */
fun playwrightWebViewFactory(param: WebViewFactoryParam): NativeWebView = PlaywrightWebView(param)
