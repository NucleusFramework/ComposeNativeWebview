package io.github.kdroidfilter.webview.web

import io.github.kdroidfilter.webview.jsbridge.WebViewJsBridge
import io.github.kdroidfilter.webview.util.KLogger
import kotlinx.coroutines.CoroutineScope
import java.io.ByteArrayOutputStream
import java.net.URL
import javax.imageio.ImageIO

internal class DesktopWebView(
    override val nativeWebView: NativeWebView,
    override val scope: CoroutineScope,
    override val webViewJsBridge: WebViewJsBridge?,
) : IWebView {
    init {
        initWebView()
    }

    override fun canGoBack(): Boolean = nativeWebView.canGoBack()

    override fun canGoForward(): Boolean = nativeWebView.canGoForward()

    override fun loadUrl(
        url: String,
        additionalHttpHeaders: Map<String, String>,
    ) {
        nativeWebView.loadUrl(url, additionalHttpHeaders)
    }

    override suspend fun loadHtml(
        html: String?,
        baseUrl: String?,
        mimeType: String?,
        encoding: String?,
        historyUrl: String?,
    ) {
        if (html == null) return
        nativeWebView.loadHtml(html)
    }

    override suspend fun loadHtmlFile(
        fileName: String,
        readType: WebViewFileReadType,
    ) {
        val html = runCatching {
            when (readType) {
                WebViewFileReadType.ASSET_RESOURCES -> {
                    val normalized = fileName.removePrefix("/")
                    val candidates = linkedSetOf<String>()
                    if (
                        normalized.startsWith("assets/") ||
                        normalized.startsWith("compose-resources/") ||
                        normalized.startsWith("composeResources/")
                    ) {
                        candidates.add(normalized)
                    }
                    candidates.add("assets/$normalized")
                    candidates.add("compose-resources/files/$normalized")
                    candidates.add("compose-resources/assets/$normalized")
                    candidates.add("composeResources/files/$normalized")
                    candidates.add("composeResources/assets/$normalized")
                    val loaders =
                        listOfNotNull(Thread.currentThread().contextClassLoader, this::class.java.classLoader)
                    candidates.firstNotNullOfOrNull { path ->
                        loaders.firstNotNullOfOrNull { loader ->
                            loader.getResourceAsStream(path)
                        }?.use { it.readBytes().toString(Charsets.UTF_8) }
                    } ?: error("Resource not found: ${candidates.joinToString()}")
                }

                WebViewFileReadType.COMPOSE_RESOURCE_FILES ->
                    URL(fileName).openStream().use { it.readBytes().toString(Charsets.UTF_8) }
            }
        }.getOrElse { e ->
            // language=HTML
            val errorHtml = """
                <!DOCTYPE html>
                <html>
                <head><title>Error Loading File</title></head>
                <body>
                  <h2>Error Loading File</h2>
                  <p>File: $fileName (ReadType: $readType)</p>
                  <pre>${e.stackTraceToString()}</pre>
                </body>
                </html>
            """.trimIndent()
            KLogger.e(e, tag = "DesktopWebView") { "loadHtmlFile failed" }
            errorHtml
        }
        nativeWebView.loadHtml(html)
    }

    override fun goBack() = nativeWebView.goBack()

    override fun goForward() = nativeWebView.goForward()

    override fun reload() = nativeWebView.reload()

    override fun stopLoading() = nativeWebView.stopLoading()

    override fun evaluateJavaScript(script: String, callback: ((String) -> Unit)?) {
        KLogger.d {
            "evaluateJavaScript: $script"
        }
        nativeWebView.evaluateJavaScript(script) { result ->
            callback?.invoke(result)
        }
    }

    override suspend fun captureScreenshotOrNull(): ByteArray? {
        val nativeBytes = nativeWebView.captureScreenshotNative()
        if (nativeBytes != null) return nativeBytes

        return runCatching {
            val image = nativeWebView.captureScreenshot(null)
            val outputStream = ByteArrayOutputStream()
            ImageIO.write(image, "png", outputStream)
            outputStream.toByteArray()
        }.getOrNull()
    }

    override fun injectJsBridge() {
        val bridge = webViewJsBridge ?: return
        super.injectJsBridge()

        //language=JavaScript
        val js = """
            if (window.${bridge.jsBridgeName} && window.ipc && window.ipc.postMessage) {
                window.${bridge.jsBridgeName}.postMessage = function (message) {
                    window.ipc.postMessage(message);
                };
            }
        """.trimIndent()
        evaluateJavaScript(js)
    }

    override fun initJsBridge(webViewJsBridge: WebViewJsBridge) {
        // No-op: IPC is configured in the Rust layer via wry's `with_ipc_handler`.
    }
}
