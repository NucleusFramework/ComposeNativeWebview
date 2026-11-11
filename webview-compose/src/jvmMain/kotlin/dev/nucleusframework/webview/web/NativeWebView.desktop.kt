package dev.nucleusframework.webview.web

import dev.nucleusframework.webview.jsbridge.JsMessageSender

/**
 * Desktop [NativeWebView] base.
 *
 * With the Tao backend, the default factory creates:
 *  - [dev.nucleusframework.webview.web.linux.LinuxWebKitNativeWebView] on Linux
 *  - [dev.nucleusframework.webview.web.macos.MacOsWebKitNativeWebView] on macOS
 *  - [dev.nucleusframework.webview.web.windows.WindowsWebView2NativeWebView] on Windows
 */
/** A raw page message and who sent it, as drained from a native backend. */
data class NativeIpcMessage(
    val body: String,
    val sender: JsMessageSender?,
)

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

    /**
     * [drainIpcMessages] with the sender the engine reported for each message. Backends whose
     * engine does not report one keep the default, which leaves every sender null.
     */
    open fun drainIpcMessagesWithSender(): List<NativeIpcMessage> =
        drainIpcMessages().map { NativeIpcMessage(body = it, sender = null) }

    open fun addNavigateListener(listener: (String) -> Boolean) = Unit

    open fun removeNavigateListener(listener: (String) -> Boolean) = Unit

    open fun captureScreenshotNative(): ByteArray? = null

    open fun openDevTools() = Unit

    open fun closeDevTools() = Unit

    open fun focus() = Unit

    open fun destroy() = Unit
}
