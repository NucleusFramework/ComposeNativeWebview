package dev.nucleusframework.webview.web

/**
 * Desktop [NativeWebView] placeholder.
 *
 * Desktop WebView is currently a no-op: all operations are intentionally empty
 * so the shared API compiles and demos run without a native backend.
 */
actual open class NativeWebView {
    open fun isReady(): Boolean = false

    open fun isLoading(): Boolean = false

    open fun getCurrentUrl(): String? = null

    open fun getTitle(): String? = null

    open fun canGoBack(): Boolean = false

    open fun canGoForward(): Boolean = false

    open fun loadUrl(url: String, additionalHttpHeaders: Map<String, String> = emptyMap()) = Unit

    open fun loadHtml(html: String) = Unit

    open fun goBack() = Unit

    open fun goForward() = Unit

    open fun reload() = Unit

    open fun stopLoading() = Unit

    open fun evaluateJavaScript(script: String, callback: (String) -> Unit = {}) {
        callback("")
    }

    open fun drainIpcMessages(): List<String> = emptyList()

    open fun addNavigateListener(listener: (String) -> Boolean) = Unit

    open fun removeNavigateListener(listener: (String) -> Boolean) = Unit

    open fun captureScreenshotNative(): ByteArray? = null

    open fun openDevTools() = Unit

    open fun closeDevTools() = Unit

    open fun focus() = Unit

    open fun destroy() = Unit
}
