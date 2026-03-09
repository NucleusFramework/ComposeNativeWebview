package io.github.kdroidfilter.webview.setting

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
        var transparent: Boolean = true,
        var dataDirectory: String? = null,
        var initScript: String? = null,
        var enableClipboard: Boolean = true,
        var enableDevtools: Boolean = false,
        var enableNavigationGestures: Boolean = true,
        var incognito: Boolean = false,
        var autoplayWithoutUserInteraction: Boolean = false,
        var focused: Boolean = true,
        var proxyConfig: ProxyConfig? = null,
    ) : PlatformWebSettings()

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
