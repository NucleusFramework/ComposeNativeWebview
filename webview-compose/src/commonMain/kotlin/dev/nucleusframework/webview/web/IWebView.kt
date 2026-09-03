package dev.nucleusframework.webview.web

import dev.nucleusframework.webview.jsbridge.WebViewJsBridge
import dev.nucleusframework.webview.jsbridge.jsBridgeObjectScript
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

expect class NativeWebView

/**
 * Platform WebView abstraction.
 */
interface IWebView {
    val nativeWebView: NativeWebView

    val scope: CoroutineScope

    val webViewJsBridge: WebViewJsBridge?

    fun canGoBack(): Boolean

    fun canGoForward(): Boolean

    fun loadUrl(
        url: String,
        additionalHttpHeaders: Map<String, String> = emptyMap(),
    )

    suspend fun loadHtml(
        html: String? = null,
        baseUrl: String? = null,
        mimeType: String? = "text/html",
        encoding: String? = "utf-8",
        historyUrl: String? = null,
    )

    suspend fun loadHtmlFile(
        fileName: String,
        readType: WebViewFileReadType,
    )

    fun goBack()

    fun goForward()

    fun reload()

    fun stopLoading()

    fun evaluateJavaScript(
        script: String,
        callback: ((String) -> Unit)? = null
    )

    /**
     * Captures a screenshot of the WebView.
     * Returns a [ByteArray] containing the image data (typically PNG), or null if failed.
     */
    suspend fun captureScreenshotOrNull(): ByteArray?

    /**
     * Returns the HTML content of the WebView as a string.
     */
    suspend fun printToStringOrNull(): String? {
        return suspendCancellableCoroutine { continuation ->
            evaluateJavaScript("document.documentElement.outerHTML") { result ->
                continuation.resume(result)
            }
        }
    }

    suspend fun loadContent(content: WebContent) {
        when (content) {
            is WebContent.Url -> loadUrl(content.url, content.additionalHttpHeaders)
            is WebContent.Data ->
                loadHtml(
                    content.data,
                    content.baseUrl,
                    content.mimeType,
                    content.encoding,
                    content.historyUrl,
                )

            is WebContent.File -> loadHtmlFile(content.fileName, content.readType)
            WebContent.NavigatorOnly -> Unit
        }
    }

    fun injectJsBridge() {
        val bridge = webViewJsBridge ?: return
        // Transport is attached by the platform override right after this call.
        evaluateJavaScript(
            jsBridgeObjectScript(bridge.jsBridgeName, "/* platform override */"),
        )
    }

    fun initJsBridge(webViewJsBridge: WebViewJsBridge)

    fun initWebView() {
        webViewJsBridge?.let { initJsBridge(it) }
    }
}
