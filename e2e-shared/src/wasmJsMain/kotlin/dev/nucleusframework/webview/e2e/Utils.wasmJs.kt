package dev.nucleusframework.webview.e2e

@OptIn(ExperimentalWasmJsInterop::class)
internal actual fun nowTimestamp(): String = js(
    "new Date().toISOString().slice(11, 19)"
)

@OptIn(ExperimentalWasmJsInterop::class)
private fun jsNow(): Double = js("Date.now()")

internal actual fun currentTimeMillis(): Long = jsNow().toLong()
