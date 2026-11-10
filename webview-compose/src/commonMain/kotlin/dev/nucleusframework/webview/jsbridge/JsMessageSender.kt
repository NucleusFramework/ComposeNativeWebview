package dev.nucleusframework.webview.jsbridge

/**
 * Who sent a [JsMessage], as reported by the web engine itself and never by the page.
 *
 * A handler that acts on a message's authority (showing a permission prompt, returning
 * account data) should check it: the bridge object is installed in every frame, so an
 * embedded third-party frame can call the same handlers as the page that embeds it.
 *
 * @property origin The sending document's origin, serialized as `scheme://host[:port]`,
 *   or `"null"` for an opaque origin (sandboxed frames, `data:` documents).
 * @property isMainFrame Whether the message came from the top-level document.
 */
data class JsMessageSender(
    val origin: String,
    val isMainFrame: Boolean,
)
