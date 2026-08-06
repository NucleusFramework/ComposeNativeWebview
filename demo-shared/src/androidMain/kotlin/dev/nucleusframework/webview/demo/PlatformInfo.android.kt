package dev.nucleusframework.webview.demo

import android.os.Build

internal actual fun platformInfoJson(): String {
    val device = Build.MODEL.orEmpty()
    val brand = Build.BRAND.orEmpty()
    val sdk = Build.VERSION.SDK_INT.toString()
    val release = Build.VERSION.RELEASE.orEmpty()
    return """{"platform":"android","brand":"$brand","device":"$device","sdk":$sdk,"osVersion":"$release"}"""
}
