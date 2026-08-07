package dev.nucleusframework.webview.e2e

import platform.UIKit.UIDevice

internal actual fun platformInfoJson(): String {
    val device = UIDevice.currentDevice
    val osName = device.systemName ?: "iOS"
    val osVersion = device.systemVersion ?: ""
    val model = device.model ?: ""
    return """{"platform":"ios","os":"$osName","osVersion":"$osVersion","device":"$model"}"""
}
