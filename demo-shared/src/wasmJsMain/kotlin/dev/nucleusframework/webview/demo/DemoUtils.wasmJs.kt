package dev.nucleusframework.webview.demo

@OptIn(ExperimentalWasmJsInterop::class)
internal actual fun nowTimestamp(): String = js(
    "new Date().toISOString().slice(11, 19)"
)
