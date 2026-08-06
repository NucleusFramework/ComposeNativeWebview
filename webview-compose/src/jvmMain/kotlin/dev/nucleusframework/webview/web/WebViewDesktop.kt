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
)

/**
 * Default factory: real WebKit2GTK on Linux when the native lib loads;
 * no-op placeholder on Windows / macOS (or when the lib is missing).
 */
actual fun defaultWebViewFactory(param: WebViewFactoryParam): NativeWebView {
    if (Platform.Current == Platform.Linux && WebKitLinuxBridge.isLoaded) {
        val settings = param.state.webSettings
        val desktop = settings.desktopWebSettings
        // Opaque white by default — transparent only when explicitly requested.
        val background =
            if (desktop.transparent) {
                settings.backgroundColor
            } else {
                val c = settings.backgroundColor
                if (c.alpha < 1f) androidx.compose.ui.graphics.Color.White else c.copy(alpha = 1f)
            }
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
    return NativeWebView()
}

/**
 * Desktop WebView composable.
 *
 * **Linux + Tao**: embeds a real WebKit2GTK view via [NativeView].
 * **Windows / macOS**: empty box (no-op backend).
 *
 * Outside a Tao [dev.nucleusframework.application.DecoratedWindow],
 * [NativeView] falls back to an empty box even on Linux — the WebView
 * only works with the Tao backend.
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

    val nativeWebView = remember(state, factory) {
        state.webView?.nativeWebView ?: factory(WebViewFactoryParam(state))
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
        if (nativeWebView !is LinuxWebKitNativeWebView) {
            // No-op backend: mark finished so demos don't spin forever.
            state.loadingState = LoadingState.Finished
            navigator.canGoBack = false
            navigator.canGoForward = false
        }
    }

    // Poll native state (URL / loading / title / nav) and drain IPC.
    LaunchedEffect(nativeWebView, state, navigator) {
        if (nativeWebView !is LinuxWebKitNativeWebView) return@LaunchedEffect
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

            if (!url.isNullOrBlank()) {
                if (!isLoading || state.lastLoadedUrl.isNullOrBlank()) {
                    state.lastLoadedUrl = url
                }
            }

            if (!title.isNullOrBlank()) {
                state.pageTitle = title
            }

            navigator.canGoBack = nativeWebView.canGoBack()
            navigator.canGoForward = nativeWebView.canGoForward()

            delay(120.milliseconds)
        }
    }

    LaunchedEffect(nativeWebView, webViewJsBridge) {
        if (nativeWebView !is LinuxWebKitNativeWebView || webViewJsBridge == null) {
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
    if (linuxWebView != null && LocalWebViewFactory.current == null) {
        NativeView(
            factory = { linuxWebView.asPlatformView() },
            modifier = modifier,
            update = { },
        )
        LaunchedEffect(nativeWebView) {
            onCreated(nativeWebView)
        }
    } else {
        // Test factory / non-Linux / no-op: empty layout slot.
        Box(modifier) {
            LaunchedEffect(nativeWebView) {
                onCreated(nativeWebView)
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
