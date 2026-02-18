package io.github.kdroidfilter.webview.setting

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Platform-specific settings containers.
 */
sealed class PlatformWebSettings {
    data class AndroidWebSettings(
        var allowFileAccess: Boolean = false,
        var textZoom: Int = 100,
        var useWideViewPort: Boolean = false,
    ) : PlatformWebSettings()

    data class DesktopWebSettings(
        var transparent: Boolean = true
    ) : PlatformWebSettings() {
        var proxyConfig: ProxyConfig? by mutableStateOf(null)
    }

    data class IOSWebSettings(
        var opaque: Boolean = false,
        var backgroundColor: Color? = null,
        var isInspectable: Boolean = false,
    ) : PlatformWebSettings()

    data class WasmJSWebSettings(
        var backgroundColor: Color? = null,
        var showBorder: Boolean = false,
    ) : PlatformWebSettings()
}
