package dev.nucleusframework.webview.web

import com.microsoft.playwright.Playwright
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

const val PLAYWRIGHT_PAGE_WIDTH = 1024
const val PLAYWRIGHT_PAGE_HEIGHT = 720

/**
 * A mock [NativeWebView] that uses Playwright for some operations.
 * Useful for testing without a real native WebView.
 */
class PlaywrightWebView(param: WebViewFactoryParam) : NativeWebView() {
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

    override fun stopLoading() = Unit
    override fun reload() = Unit
    override fun goBack() = Unit
    override fun goForward() = Unit

    override fun captureScreenshotNative(): ByteArray? {
        if (currentContent.startsWith("http")) {
            runCatching {
                Playwright.create().use { playwright ->
                    playwright.chromium().launch().use { browser ->
                        browser.newPage().use { page ->
                            page.setViewportSize(PLAYWRIGHT_PAGE_WIDTH, PLAYWRIGHT_PAGE_HEIGHT)
                            page.navigate(currentContent)
                            return page.screenshot()
                        }
                    }
                }
            }.onFailure {
                println("Playwright failed: ${it.message}. Falling back to mock.")
            }
        }

        val img = BufferedImage(PLAYWRIGHT_PAGE_WIDTH, PLAYWRIGHT_PAGE_HEIGHT, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.color = Color.LIGHT_GRAY
        g.fillRect(0, 0, PLAYWRIGHT_PAGE_WIDTH, PLAYWRIGHT_PAGE_HEIGHT)
        g.color = Color.BLACK
        g.drawString("Mock: $currentContent", 5, 50)
        g.dispose()
        val output = ByteArrayOutputStream()
        ImageIO.write(img, "png", output)
        return output.toByteArray()
    }

    /**
     * Convenience helper used by tests that still want a [BufferedImage].
     */
    fun captureScreenshot(): BufferedImage {
        val bytes = captureScreenshotNative() ?: return BufferedImage(
            PLAYWRIGHT_PAGE_WIDTH,
            PLAYWRIGHT_PAGE_HEIGHT,
            BufferedImage.TYPE_INT_ARGB,
        )
        return ImageIO.read(ByteArrayInputStream(bytes))
    }
}

/**
 * A factory function that creates a [PlaywrightWebView].
 */
fun playwrightWebViewFactory(param: WebViewFactoryParam): NativeWebView = PlaywrightWebView(param)
