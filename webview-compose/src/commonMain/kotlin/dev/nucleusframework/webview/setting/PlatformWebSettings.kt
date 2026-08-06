package dev.nucleusframework.webview.setting

import androidx.compose.ui.graphics.Color

/**
 * Platform-specific settings containers.
 */
sealed class PlatformWebSettings {
    data class AndroidWebSettings(
        var allowFileAccess: Boolean = false,
        var textZoom: Int = 100,
        var useWideViewPort: Boolean = false,
        var domStorageEnabled: Boolean = false
    ) : PlatformWebSettings()

    data class DesktopWebSettings(
        /**
         * When false (default), the page paints on an opaque white surface like a
         * normal browser. When true, the WebView chrome is fully transparent so
         * Compose content underneath can show through.
         */
        var transparent: Boolean = false,
        var dataDirectory: String? = null,
        var initScript: String? = null,
        var enableClipboard: Boolean = true,
        var enableDevtools: Boolean = false,
        var enableNavigationGestures: Boolean = true,
        var incognito: Boolean = false,
        var autoplayWithoutUserInteraction: Boolean = false,
        var focused: Boolean = true,
    ) : PlatformWebSettings()

    data class IOSWebSettings(
        var opaque: Boolean = false,
        var backgroundColor: Color? = null,
        var isInspectable: Boolean = false,
    ) : PlatformWebSettings()

    data class WasmJSWebSettings(
        var backgroundColor: Color? = null,
        var showBorder: Boolean = false,
        var enableSandbox: Boolean = false,
        var customContainerStyle: String? = null,
        var allowFullscreen: Boolean = true,
        var borderStyle: String = "1px solid #ccc",
        var sandboxPermissions: String = "allow-scripts allow-same-origin allow-forms",
    ) : PlatformWebSettings()
}
