package dev.nucleusframework.webview.web

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import dev.nucleusframework.core.runtime.Platform
import dev.nucleusframework.webview.cookie.DesktopCookieManager
import dev.nucleusframework.webview.jsbridge.WebViewJsBridge
import dev.nucleusframework.webview.jsbridge.parseJsMessage
import dev.nucleusframework.webview.request.WebRequest
import dev.nucleusframework.webview.request.WebRequestInterceptResult
import dev.nucleusframework.webview.web.linux.LinuxWebKitNativeWebView
import dev.nucleusframework.webview.web.linux.WebKitLinuxBridge
import dev.nucleusframework.webview.web.macos.MacOsWebKitNativeWebView
import dev.nucleusframework.webview.web.macos.WebKitMacOsBridge
import dev.nucleusframework.webview.web.windows.WebView2WindowsBridge
import dev.nucleusframework.webview.web.windows.WindowsWebView2NativeWebView
import dev.nucleusframework.window.tao.LocalTaoWindow
import dev.nucleusframework.window.tao.NativeView
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

actual class WebViewFactoryParam(
    val state: WebViewState,
    val fileContent: String = "",
    /** Windows only: parent Tao HWND. Required to create a real WebView2. */
    val parentHwnd: Long = 0L,
)

/**
 * Default factory: real WebKit2GTK on Linux, WKWebView on macOS, WebView2 on
 * Windows when the native lib loads (and Windows parent HWND is available).
 */
actual fun defaultWebViewFactory(param: WebViewFactoryParam): NativeWebView {
    val settings = param.state.webSettings
    val desktop = settings.desktopWebSettings
    val background =
        if (desktop.transparent) {
            settings.backgroundColor
        } else {
            val c = settings.backgroundColor
            if (c.alpha < 1f) androidx.compose.ui.graphics.Color.White else c.copy(alpha = 1f)
        }

    if (Platform.Current == Platform.Linux && WebKitLinuxBridge.isLoaded) {
        return LinuxWebKitNativeWebView(
            customUserAgent = settings.customUserAgentString,
            dataDirectory = desktop.dataDirectory,
            initScript = desktop.initScript,
            incognito = desktop.incognito,
            enableDevtools = desktop.enableDevtools,
            javascriptEnabled = settings.isJavaScriptEnabled,
            zoomLevel = settings.zoomLevel,
            transparent = desktop.transparent,
            backgroundColor = background,
        )
    }

    if (Platform.Current == Platform.MacOS && WebKitMacOsBridge.isLoaded) {
        return MacOsWebKitNativeWebView(
            customUserAgent = settings.customUserAgentString,
            dataDirectory = desktop.dataDirectory,
            initScript = desktop.initScript,
            incognito = desktop.incognito,
            enableDevtools = desktop.enableDevtools,
            javascriptEnabled = settings.isJavaScriptEnabled,
            zoomLevel = settings.zoomLevel,
            transparent = desktop.transparent,
            backgroundColor = background,
        )
    }

    if (
        Platform.Current == Platform.Windows &&
        WebView2WindowsBridge.isLoaded &&
        param.parentHwnd != 0L
    ) {
        return WindowsWebView2NativeWebView(
            parentHwnd = param.parentHwnd,
            customUserAgent = settings.customUserAgentString,
            dataDirectory = desktop.dataDirectory,
            initScript = desktop.initScript,
            incognito = desktop.incognito,
            enableDevtools = desktop.enableDevtools,
            javascriptEnabled = settings.isJavaScriptEnabled,
            zoomLevel = settings.zoomLevel,
            transparent = desktop.transparent,
            backgroundColor = background,
        )
    }

    return NativeWebView()
}

private fun NativeWebView.isLiveBackend(): Boolean =
    this is LinuxWebKitNativeWebView ||
        this is MacOsWebKitNativeWebView ||
        this is WindowsWebView2NativeWebView

/**
 * Desktop WebView composable.
 *
 * **Linux + Tao**: embeds a real WebKit2GTK view via [NativeView].
 * **macOS + Tao**: embeds a real WKWebView via [NativeView].
 * **Windows + Tao**: embeds a real WebView2 view via [NativeView] (DComp).
 *
 * Outside a Tao [dev.nucleusframework.application.DecoratedWindow],
 * [NativeView] falls back to an empty box — the WebView only works with
 * the Tao backend.
 */
@Composable
actual fun ActualWebView(
    state: WebViewState,
    modifier: Modifier,
    navigator: WebViewNavigator,
    webViewJsBridge: WebViewJsBridge?,
    onCreated: (NativeWebView) -> Unit,
    onDispose: (NativeWebView) -> Unit,
    factory: (WebViewFactoryParam) -> NativeWebView,
) {
    val currentOnDispose by rememberUpdatedState(onDispose)
    val scope = rememberCoroutineScope()

    val parentHwnd =
        if (Platform.Current == Platform.Windows) {
            LocalTaoWindow.current?.nativeHandle ?: 0L
        } else {
            0L
        }

    val nativeWebView = remember(state, factory, parentHwnd) {
        // Prefer a ready live backend across recompositions. Windows may
        // first compose with parentHwnd=0 (no-op) then recreate once the
        // Tao HWND is available — do not lock in a permanent no-op.
        val existing = state.webView?.nativeWebView
        if (existing != null && existing.isReady() && existing.isLiveBackend()) {
            existing
        } else {
            factory(WebViewFactoryParam(state, parentHwnd = parentHwnd))
        }
    }

    val desktopWebView = remember(nativeWebView, scope, webViewJsBridge) {
        DesktopWebView(
            nativeWebView = nativeWebView,
            scope = scope,
            webViewJsBridge = webViewJsBridge,
        )
    }

    LaunchedEffect(desktopWebView) {
        state.webView = desktopWebView
        webViewJsBridge?.webView = desktopWebView
        (state.cookieManager as? DesktopCookieManager)?.attach(nativeWebView)
        if (!nativeWebView.isLiveBackend()) {
            // No-op backend: mark finished so demos don't spin forever.
            state.loadingState = LoadingState.Finished
            navigator.canGoBack = false
            navigator.canGoForward = false
        }
    }

    // Poll native state (URL / loading / title / nav) and drain IPC.
    LaunchedEffect(nativeWebView, state, navigator) {
        if (!nativeWebView.isLiveBackend()) return@LaunchedEffect
        while (true) {
            if (!nativeWebView.isReady()) {
                if (state.loadingState !is LoadingState.Initializing) {
                    state.loadingState = LoadingState.Initializing
                }
                delay(50.milliseconds)
                continue
            }

            val isLoading = nativeWebView.isLoading()
            val url = nativeWebView.getCurrentUrl()
            val title = nativeWebView.getTitle()

            // Do NOT treat a freshly created idle WebView as Finished — isLoading
            // is false before any navigation, which would race loadHtml drivers.
            state.loadingState =
                if (isLoading) {
                    val next =
                        when (val current = state.loadingState) {
                            is LoadingState.Loading -> (current.progress + 0.02f).coerceAtMost(0.9f)
                            else -> 0.1f
                        }
                    LoadingState.Loading(next)
                } else {
                    when (state.loadingState) {
                        is LoadingState.Loading -> LoadingState.Finished
                        is LoadingState.Finished -> LoadingState.Finished
                        is LoadingState.Initializing -> {
                            val hasDocument =
                                (!url.isNullOrBlank() && url != "about:blank") ||
                                    !title.isNullOrBlank()
                            if (hasDocument) LoadingState.Finished else LoadingState.Initializing
                        }
                    }
                }

            // Always publish the latest source — do not gate on isLoading.
            // (A stuck isLoading flag must not leave lastLoadedUrl blank.)
            if (!url.isNullOrBlank()) {
                state.lastLoadedUrl = url
            }

            if (!title.isNullOrBlank()) {
                state.pageTitle = title
            }

            // Document-ready fallback: if native isLoading is stuck true but we
            // already have a real document, advance to Finished so demos/suite
            // (and JS bridge injection) don't hang.
            if (isLoading &&
                state.loadingState is LoadingState.Loading &&
                ((!url.isNullOrBlank() && url != "about:blank") || !title.isNullOrBlank())
            ) {
                state.loadingState = LoadingState.Finished
            }

            navigator.canGoBack = nativeWebView.canGoBack()
            navigator.canGoForward = nativeWebView.canGoForward()

            delay(120.milliseconds)
        }
    }

    LaunchedEffect(nativeWebView, webViewJsBridge) {
        if (!nativeWebView.isLiveBackend() || webViewJsBridge == null) {
            return@LaunchedEffect
        }
        while (true) {
            for (raw in nativeWebView.drainIpcMessages()) {
                parseJsMessage(raw)?.let { webViewJsBridge.dispatch(it) }
            }
            delay(50.milliseconds)
        }
    }

    DisposableEffect(nativeWebView, navigator) {
        val listener: (String) -> Boolean = a@{
            if (navigator.requestInterceptor == null) {
                return@a true
            }
            val webRequest =
                WebRequest(
                    url = it,
                    headers = mutableMapOf(),
                    isForMainFrame = true,
                    isRedirect = true,
                )
            return@a when (
                val interceptResult =
                    navigator.requestInterceptor.onInterceptUrlRequest(webRequest, navigator)
            ) {
                WebRequestInterceptResult.Allow -> true
                WebRequestInterceptResult.Reject -> false
                is WebRequestInterceptResult.Modify -> {
                    interceptResult.request.let { modified ->
                        navigator.stopLoading()
                        navigator.loadUrl(modified.url, modified.headers)
                    }
                    false
                }
            }
        }
        nativeWebView.addNavigateListener(listener)
        onDispose {
            nativeWebView.removeNavigateListener(listener)
        }
    }

    val linuxWebView = nativeWebView as? LinuxWebKitNativeWebView
    val macosWebView = nativeWebView as? MacOsWebKitNativeWebView
    val windowsWebView = nativeWebView as? WindowsWebView2NativeWebView
    when {
        linuxWebView != null && LocalWebViewFactory.current == null -> {
            NativeView(
                factory = { linuxWebView.asPlatformView() },
                modifier = modifier,
                update = { },
            )
            LaunchedEffect(nativeWebView) {
                onCreated(nativeWebView)
            }
        }
        macosWebView != null && LocalWebViewFactory.current == null -> {
            NativeView(
                factory = { macosWebView.asPlatformView() },
                modifier = modifier,
                update = { },
            )
            LaunchedEffect(nativeWebView) {
                onCreated(nativeWebView)
            }
        }
        windowsWebView != null && LocalWebViewFactory.current == null -> {
            NativeView(
                factory = { windowsWebView.asPlatformView() },
                modifier = modifier,
                update = { },
            )
            LaunchedEffect(nativeWebView) {
                onCreated(nativeWebView)
            }
        }
        else -> {
            // Test factory / unsupported / no-op: empty layout slot.
            Box(modifier) {
                LaunchedEffect(nativeWebView) {
                    onCreated(nativeWebView)
                }
            }
        }
    }

    DisposableEffect(nativeWebView) {
        onDispose {
            state.webView = null
            webViewJsBridge?.webView = null
            (state.cookieManager as? DesktopCookieManager)?.attach(null)
            currentOnDispose(nativeWebView)
            nativeWebView.destroy()
        }
    }
}

/**
 * Captures a screenshot of the WebView and returns it as a [BufferedImage].
 */
suspend fun IWebView.toAwtImage(): BufferedImage? {
    val bytes = captureScreenshotOrNull() ?: return null
    return withContext(Dispatchers.IO) {
        ImageIO.read(ByteArrayInputStream(bytes))
    }
}
