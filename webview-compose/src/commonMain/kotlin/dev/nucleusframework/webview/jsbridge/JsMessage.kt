package dev.nucleusframework.webview.jsbridge

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Message dispatched from JS to native.
 *
 * `params` is expected to be a JSON string (API compatibility with compose-webview-multiplatform).
 *
 * @property sender Who sent the message, when the engine reports it; null on the platforms
 *   that do not. It is set by the native side only and is never read from the page's JSON.
 */
@Serializable
data class JsMessage(
    val callbackId: Int,
    val methodName: String,
    val params: String,
    @Transient val sender: JsMessageSender? = null,
)

