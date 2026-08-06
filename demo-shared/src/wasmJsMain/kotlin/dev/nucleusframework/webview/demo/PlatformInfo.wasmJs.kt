package dev.nucleusframework.webview.demo

import kotlinx.browser.window
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal actual fun platformInfoJson(): String = buildJsonObject {
    put("platform", "wasmJs")
    put("runtime", "browser")
    put("userAgent", window.navigator.userAgent)
    put("language", window.navigator.language)
}.toString()
