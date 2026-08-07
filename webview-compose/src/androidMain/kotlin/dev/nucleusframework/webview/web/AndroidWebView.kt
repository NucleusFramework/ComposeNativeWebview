package dev.nucleusframework.webview.web

import android.graphics.Bitmap
import android.graphics.Bitmap.createBitmap
import android.graphics.Canvas
import android.webkit.JavascriptInterface
import android.webkit.WebView
import dev.nucleusframework.webview.jsbridge.WebViewJsBridge
import dev.nucleusframework.webview.jsbridge.parseJsMessage
import dev.nucleusframework.webview.util.KLogger
import kotlinx.coroutines.CoroutineScope
import java.io.ByteArrayOutputStream

internal class AndroidWebView(
    override val nativeWebView: WebView,
    override val scope: CoroutineScope,
    override val webViewJsBridge: WebViewJsBridge?,
) : IWebView {
    init {
        initWebView()
    }

    override fun canGoBack(): Boolean = nativeWebView.canGoBack()

    override fun canGoForward(): Boolean = nativeWebView.canGoForward()

    override fun loadUrl(url: String, additionalHttpHeaders: Map<String, String>) {
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
        nativeWebView.loadDataWithBaseURL(baseUrl, html, mimeType, encoding, historyUrl)
    }

    override suspend fun loadHtmlFile(fileName: String, readType: WebViewFileReadType) {
        KLogger.d(tag = "AndroidWebView") { "loadHtmlFile fileName=$fileName readType=$readType" }
        val normalized = fileName.removePrefix("/")
        val assetPath = normalized.removePrefix("assets/")
        when (readType) {
            WebViewFileReadType.ASSET_RESOURCES -> {
                val candidates =
                    listOf(
                        "compose-resources/files/$assetPath",
                        "composeResources/files/$assetPath",
                        "compose-resources/assets/$assetPath",
                        "composeResources/assets/$assetPath",
                        assetPath,
                    )
                val selected =
                    candidates.firstOrNull { path ->
                        try {
                            nativeWebView.context.assets.open(path).close()
                            true
                        } catch (_: Exception) {
                            false
                        }
                    } ?: candidates.first()
                val url = "file:///android_asset/$selected"
                nativeWebView.loadUrl(url)
                KLogger.d(tag = "AndroidWebView") { "loadUrl $url (candidates: ${candidates.joinToString()})" }
            }

            WebViewFileReadType.COMPOSE_RESOURCE_FILES -> nativeWebView.loadUrl(fileName)
        }
    }

    override fun goBack() = nativeWebView.goBack()

    override fun goForward() = nativeWebView.goForward()

    override fun reload() = nativeWebView.reload()

    override fun stopLoading() = nativeWebView.stopLoading()

    override suspend fun captureScreenshotOrNull(): ByteArray? {
        return runCatching {
            val bitmap = createBitmap(
                nativeWebView.width.coerceAtLeast(1),
                nativeWebView.height.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            nativeWebView.draw(canvas)
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.toByteArray()
        }.getOrNull()
    }

    override fun evaluateJavaScript(script: String, callback: ((String) -> Unit)?) {
        // evaluateJavascript must run on the WebView/UI thread (not the binder
        // thread used by @JavascriptInterface).
        nativeWebView.post {
            KLogger.d { "evaluateJavaScript: $script" }
            nativeWebView.evaluateJavascript(script) { result ->
                callback?.invoke(result ?: "")
            }
        }
    }

    override fun injectJsBridge() {
        val bridge = webViewJsBridge ?: return
        super.injectJsBridge()
        val js =
            """
            if (window.${bridge.jsBridgeName} && window.androidJsBridge && window.androidJsBridge.call) {
              window.${bridge.jsBridgeName}.postMessage = function (message) {
                window.androidJsBridge.call(message);
              };
            }
            """.trimIndent()
        evaluateJavaScript(js)
    }

    override fun initJsBridge(webViewJsBridge: WebViewJsBridge) {
        nativeWebView.addJavascriptInterface(this, "androidJsBridge")
    }

    @JavascriptInterface
    fun call(raw: String) {
        // Hop to the WebView thread before dispatch/callback evaluation.
        nativeWebView.post {
            parseJsMessage(raw)?.let { message ->
                webViewJsBridge?.dispatch(message)
            } ?: run {
                KLogger.w(tag = "AndroidWebView") { "Invalid JS message: $raw" }
            }
        }
    }
}
