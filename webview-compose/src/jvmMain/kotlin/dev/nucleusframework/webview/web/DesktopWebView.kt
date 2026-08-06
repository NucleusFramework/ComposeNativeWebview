package dev.nucleusframework.webview.web

import dev.nucleusframework.webview.jsbridge.WebViewJsBridge
import kotlinx.coroutines.CoroutineScope

/**
 * Desktop [IWebView] implementation. All operations are no-ops until a native
 * backend is wired back in.
 */
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
    ) = Unit

    override fun goBack() = nativeWebView.goBack()

    override fun goForward() = nativeWebView.goForward()

    override fun reload() = nativeWebView.reload()

    override fun stopLoading() = nativeWebView.stopLoading()

    override fun evaluateJavaScript(script: String, callback: ((String) -> Unit)?) {
        nativeWebView.evaluateJavaScript(script) { result ->
            callback?.invoke(result)
        }
    }

    override suspend fun captureScreenshotOrNull(): ByteArray? =
        nativeWebView.captureScreenshotNative()

    override fun injectJsBridge() {
        // No-op on desktop until a native JS bridge backend is available.
    }

    override fun initJsBridge(webViewJsBridge: WebViewJsBridge) = Unit
}
