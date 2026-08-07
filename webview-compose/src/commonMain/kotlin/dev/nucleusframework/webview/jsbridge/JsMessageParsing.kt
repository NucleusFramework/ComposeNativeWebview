package dev.nucleusframework.webview.jsbridge

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

private val jsBridgeJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class ParsedJsBridgeMessage(
    val callbackId: Int? = null,
    val methodName: String? = null,
    val action: String? = null,
    val params: JsonElement? = null,
    val type: String? = null,
)

internal fun parseJsMessage(
    raw: String,
    expectedType: String? = null,
): JsMessage? = runCatching {
    val message = jsBridgeJson.decodeFromString<ParsedJsBridgeMessage>(raw)
    if (expectedType != null && message.type != expectedType) return null

    val methodName = message.methodName ?: message.action ?: return null
    val isWasmBridgeMessage = message.action != null
    val params = message.params?.toJsMessageParams() ?: if (isWasmBridgeMessage) "{}" else ""
    val callbackId = message.callbackId ?: if (isWasmBridgeMessage) 0 else -1

    JsMessage(
        callbackId = callbackId,
        methodName = methodName,
        params = params
    )
}.getOrNull()

private fun JsonElement.toJsMessageParams(): String = if (
    this is JsonPrimitive &&
    isString
) {
    content
} else {
    toString()
}
