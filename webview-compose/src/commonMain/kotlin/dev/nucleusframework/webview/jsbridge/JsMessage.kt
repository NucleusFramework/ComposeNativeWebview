package dev.nucleusframework.webview.jsbridge

import kotlinx.serialization.Serializable

/**
 * Message dispatched from JS to native.
 *
 * `params` is expected to be a JSON string (API compatibility with compose-webview-multiplatform).
 */
@Serializable
data class JsMessage(
    val callbackId: Int,
    val methodName: String,
    val params: String
)

