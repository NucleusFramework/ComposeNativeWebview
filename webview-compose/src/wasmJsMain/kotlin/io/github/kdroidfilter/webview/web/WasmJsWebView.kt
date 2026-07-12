@file:OptIn(ExperimentalWasmJsInterop::class)
package io.github.kdroidfilter.webview.web

import io.github.kdroidfilter.webview.jsbridge.WebViewJsBridge
import io.github.kdroidfilter.webview.util.KLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.w3c.dom.HTMLIFrameElement
import kotlin.js.Promise
import kotlin.time.Duration.Companion.milliseconds

/**
 * The native web view implementation for WasmJs platform.
 * Uses an HTML iframe element as the underlying implementation.
 */
actual class NativeWebView(
    val element: HTMLIFrameElement
)

/**
 * WebView adapter for WasmJs that implements the IWebView interface
 */
class WasmJsWebView(
    private val element: HTMLIFrameElement,
    override val nativeWebView: NativeWebView,
    override val scope: CoroutineScope,
    override val webViewJsBridge: WebViewJsBridge?,
    var onLoadStarted: (() -> Unit)? = null,
) : IWebView {
    override fun canGoBack(): Boolean = element.contentWindow?.history?.length?.let {
        it > 1
    } ?: false

    // Browser iframe history API does not expose whether forward navigation is available.
    // history.length only gives total entries, not the current position within the stack.
    override fun canGoForward(): Boolean = false

    override fun loadUrl(
        url: String,
        additionalHttpHeaders: Map<String, String>
    ) {
        try {
            onLoadStarted?.invoke()
            element.src = url
            if (webViewJsBridge != null) {
                scope.launch {
                    delay(500.milliseconds)
                    injectJsBridge()
                }
            }
        } catch (e: Exception) {
            KLogger.e(
                t = e,
                tag = "WasmJsWebView"
            ) {
                "Error setting URL: $url"
            }
        }
    }

    override suspend fun loadHtml(
        html: String?,
        baseUrl: String?,
        mimeType: String?,
        encoding: String?,
        historyUrl: String?
    ) {
        try {
            if (html != null) {
                onLoadStarted?.invoke()
                val htmlWithBridge = if (webViewJsBridge != null) {
                    injectBridgeIntoHtml(
                        htmlContent = html,
                        jsBridgeName = webViewJsBridge.jsBridgeName
                    )
                } else {
                    html
                }
                element.srcdoc = htmlWithBridge
            }
        } catch (e: Exception) {
            KLogger.e(
                t = e,
                tag = "WasmJsWebView"
            ) {
                "Error setting HTML: $html"
            }
        }
    }

    override suspend fun loadHtmlFile(
        fileName: String,
        readType: WebViewFileReadType,
    ) {
        try {
            val url = when (readType) {
                WebViewFileReadType.ASSET_RESOURCES -> "assets/$fileName"
                WebViewFileReadType.COMPOSE_RESOURCE_FILES -> fileName
            }
            onLoadStarted?.invoke()
            element.src = url

            if (webViewJsBridge != null) {
                scope.launch {
                    delay(1000)
                    injectJsBridge()
                }
            }
        } catch (e: Exception) {
            val fallbackHtml =
                //language=HTML
                """
                <!DOCTYPE html>
                <html>
                <body>
                    <h2 style="color:red;">Failed to load file: $fileName</h2>
                    <p>Error: ${e.message}</p>
                </body>
                </html>
                """.trimIndent()
            loadHtml(
                html = fallbackHtml,
                mimeType = null,
                encoding = null
            )
        }
    }

    override fun goBack() {
        try {
            element.contentWindow?.history?.back()
        } catch (e: Exception) {
            KLogger.e(
                t = e,
                tag = "HtmlView"
            ) {
                "Failed to go back in history"
            }
        }
    }

    override fun goForward() {
        try {
            element.contentWindow?.history?.forward()
        } catch (e: Exception) {
            KLogger.e(
                t = e,
                tag = "HtmlView"
            ) {
                "Failed to go forward in history"
            }
        }
    }

    override fun reload() {
        try {
            onLoadStarted?.invoke()
            element.contentWindow?.location?.reload()
        } catch (e: Exception) {
            KLogger.e(
                t = e,
                tag = "HtmlView"
            ) {
                "Failed to reload page"
            }
        }
    }

    override fun stopLoading() {
        try {
            element.contentWindow?.stop()
        } catch (e: Exception) {
            KLogger.e(
                t = e,
                tag = "HtmlView"
            ) {
                "Failed to stop loading"
            }
        }
    }

    /**
     * Captures a screenshot of the current WebView content.
     * Note: On WasmJs, this requires the 'html2canvas' library to be available globally.
     */
    override suspend fun captureScreenshotOrNull(): ByteArray? {
        return try {
            val promise: Promise<JsAny?> = captureScreenshotJs(element)
            val uint8Array = promise.await() as? Uint8Array
            uint8Array?.let { array ->
                ByteArray(array.length) { array[it] } 
            }
        } catch (e: Exception) {
            KLogger.e(
                t = e,
                tag = "WasmJsWebView"
            ) { 
                "Failed to capture screenshot"
            }
            null
        }
    }

    override fun evaluateJavaScript(
        script: String,
        callback: ((String) -> Unit)?,
    ) {
        scope.launch {
            try {
                val result = evaluateScriptJs(element, script)
                callback?.invoke(result)
            } catch (t: Throwable) {
                callback?.invoke("Error: ${t.message}")
            }
        }
    }

    override fun injectJsBridge() {
        if (webViewJsBridge == null) return
        super.injectJsBridge()

        val bridgeScript = createJsBridgeScript(webViewJsBridge.jsBridgeName, true)
        evaluateJavaScript(bridgeScript)
        // Message handling is done by the single listener registered in setupJsBridgeForWasm()
    }

    override fun initJsBridge(webViewJsBridge: WebViewJsBridge) {
        // Bridge initialization is handled externally
    }

    /**
     * Inject JS bridge script into an HTML content
     */
    private fun injectBridgeIntoHtml(
        htmlContent: String,
        jsBridgeName: String,
    ): String {
        val bridgeScriptContent = createJsBridgeScript(jsBridgeName)
        val bridgeScript =
            """
            <script>
            // KMP WebView Bridge - Must be loaded first
            $bridgeScriptContent
            </script>
            """.trimIndent()

        if (htmlContent.contains("<head>")) {
            return htmlContent.replace("<head>", "<head>$bridgeScript")
        }

        val headPattern = "<head[^>]*>".toRegex()
        val headMatch = headPattern.find(htmlContent)
        if (headMatch != null) {
            return htmlContent.replace(headMatch.value, "${headMatch.value}$bridgeScript")
        }

        if (htmlContent.contains("<body>") || htmlContent.contains("<body ")) {
            val bodyPattern = "<body[^>]*>".toRegex()
            return bodyPattern.replace(htmlContent, "$0$bridgeScript")
        }

        return "$bridgeScript$htmlContent"
    }
}
