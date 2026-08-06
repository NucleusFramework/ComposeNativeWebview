package dev.nucleusframework.webview.web

/**
 * Desktop [NativeWebView] base.
 *
 * On Linux with the Tao backend, the default factory creates a
 * [dev.nucleusframework.webview.web.linux.LinuxWebKitNativeWebView].
 * Windows and macOS remain no-ops until their native backends land.
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
