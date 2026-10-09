package dev.nucleusframework.webview.setting

/**
 * Which color scheme a [dev.nucleusframework.webview.web.IWebView] should render its loaded
 * content in, independently of whether that content declares its own `color-scheme`/
 * `prefers-color-scheme` support.
 *
 * Only desktop (Linux/macOS/Windows) backends need this spelled out explicitly - unlike every
 * other engine this library targets, none of WKWebView, WebView2, or WebKit2GTK resolves
 * `prefers-color-scheme` from the system theme on its own when embedded without a full browser
 * chrome around it, so content that relies on that (rather than setting every color explicitly)
 * renders as light regardless of the host app's own theme unless told otherwise. Android
 * (`androidx.webkit.WebSettingsCompat.setAlgorithmicDarkeningAllowed`) and iOS/Wasm already behave
 * correctly without this, so [IWebView.setColorScheme] is a no-op there.
 */
enum class WebViewColorScheme {
    /** Match the host OS's current light/dark setting. The default - matches this library's
     *  existing behavior for every caller that doesn't opt in. */
    SYSTEM,
    LIGHT,
    DARK,
}
