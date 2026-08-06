package dev.nucleusframework.webview.web

/**
 * Previously hosted a Playwright-backed mock [NativeWebView].
 *
 * Desktop verification is now end-to-end against real WebKit2GTK + Tao
 * (see `LinuxWebViewE2eTest` in `:webview-compose`). This type remains
 * only so older imports fail loudly at compile time with a clear message.
 */
@Deprecated(
    message = "Use real desktop e2e (LinuxWebViewE2eTest / WebKit2GTK + Tao), not synthetic mocks",
    level = DeprecationLevel.ERROR,
)
class PlaywrightWebView
