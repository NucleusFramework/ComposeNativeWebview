package dev.nucleusframework.webview.jsbridge

/**
 * Who sent a [JsMessage], as reported by the web engine itself and never by the page.
 *
 * A handler that acts on a message's authority (showing a permission prompt, returning
 * account data) should check it: the bridge object is installed in every frame, so an
 * embedded third-party frame can call the same handlers as the page that embeds it.
 *
 * What the desktop engines report:
 *  - macOS (WKWebView): both properties, for every frame.
 *  - Windows (WebView2): only the top-level document's messages reach the bridge, so
 *    [isMainFrame] is always true there, and [origin] is not reported.
 *
 * @property origin The sending document's origin, serialized as `scheme://host[:port]`,
 *   or `"null"` for an opaque origin (sandboxed frames, `data:` documents). Null when the
 *   engine does not report an origin.
 * @property isMainFrame Whether the message came from the top-level document.
 */
data class JsMessageSender(
    val origin: String?,
    val isMainFrame: Boolean,
)
