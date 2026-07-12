package io.github.kdroidfilter.webview.web

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.kdroidfilter.webview.cookie.WryCookieManager
import io.github.kdroidfilter.webview.jsbridge.WebViewJsBridge
import io.github.kdroidfilter.webview.jsbridge.parseJsMessage
import io.github.kdroidfilter.webview.request.WebRequest
import io.github.kdroidfilter.webview.request.WebRequestInterceptResult
import io.github.kdroidfilter.webview.wry.Rgba
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.milliseconds

actual class WebViewFactoryParam(
    val state: WebViewState,
    val fileContent: String = "",
)

actual fun defaultWebViewFactory(
    param: WebViewFactoryParam
): NativeWebView = when (val content = param.state.content) {
    is WebContent.Url -> NativeWebView(
        initialUrl = content.url,
        customUserAgent = param.state.webSettings.customUserAgentString,
        dataDirectory = param.state.webSettings.desktopWebSettings.dataDirectory,
        supportZoom = param.state.webSettings.supportZoom,
        backgroundColor = param.state.webSettings.backgroundColor.toRgba(),
        transparent = param.state.webSettings.desktopWebSettings.transparent,
        initScript = param.state.webSettings.desktopWebSettings.initScript,
        enableClipboard = param.state.webSettings.desktopWebSettings.enableClipboard,
        enableDevtools = param.state.webSettings.desktopWebSettings.enableDevtools,
        enableNavigationGestures = param.state.webSettings.desktopWebSettings.enableNavigationGestures,
        incognito = param.state.webSettings.desktopWebSettings.incognito,
        autoplayWithoutUserInteraction = param.state.webSettings.desktopWebSettings.autoplayWithoutUserInteraction,
        focused = param.state.webSettings.desktopWebSettings.focused
    )

    else -> NativeWebView(
        initialUrl = "about:blank",
        customUserAgent = param.state.webSettings.customUserAgentString,
        dataDirectory = param.state.webSettings.desktopWebSettings.dataDirectory,
        supportZoom = param.state.webSettings.supportZoom,
        backgroundColor = param.state.webSettings.backgroundColor.toRgba(),
        transparent = param.state.webSettings.desktopWebSettings.transparent,
        initScript = param.state.webSettings.desktopWebSettings.initScript,
        enableClipboard = param.state.webSettings.desktopWebSettings.enableClipboard,
        enableDevtools = param.state.webSettings.desktopWebSettings.enableDevtools,
        enableNavigationGestures = param.state.webSettings.desktopWebSettings.enableNavigationGestures,
        incognito = param.state.webSettings.desktopWebSettings.incognito,
        autoplayWithoutUserInteraction = param.state.webSettings.desktopWebSettings.autoplayWithoutUserInteraction,
        focused = param.state.webSettings.desktopWebSettings.focused
    )
}

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

    val desiredSettingsKey = state.webSettings.let {
        listOf(
            it.customUserAgentString?.trim()?.takeIf(String::isNotEmpty),
            it.supportZoom,
            it.backgroundColor,
        )
    }

    var effectiveSettingsKey by remember { mutableStateOf(desiredSettingsKey) }

    LaunchedEffect(desiredSettingsKey) {
        if (desiredSettingsKey != effectiveSettingsKey) {
            delay(400.milliseconds)
            effectiveSettingsKey = desiredSettingsKey
        }
    }

    key(effectiveSettingsKey) {
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
            (state.cookieManager as? WryCookieManager)?.attach(nativeWebView)
        }

    // Poll native state (URL/loading/title/nav) and drain IPC messages for JS bridge.
    listOf(nativeWebView, state, navigator, webViewJsBridge).let {
        LaunchedEffect(it) {
            while (true) {
                if (!nativeWebView.isReady()) {
                    if (state.loadingState !is LoadingState.Initializing) {
                        state.loadingState = LoadingState.Initializing
                    }
                    delay(50.milliseconds)
                    continue
                }

                val isLoading = nativeWebView.isLoading()
                state.loadingState =
                    if (isLoading) {
                            val next =
                                when (val current = state.loadingState) {
                                    is LoadingState.Loading -> (current.progress + 0.02f).coerceAtMost(0.9f)
                                    else -> 0.1f
                                }
                            LoadingState.Loading(next)
                        } else {
                            LoadingState.Finished
                        }

                    val url = nativeWebView.getCurrentUrl()
                    if (!url.isNullOrBlank()) {
                        if (!isLoading || state.lastLoadedUrl.isNullOrBlank()) {
                            state.lastLoadedUrl = url
                        }
                    }

                    val title = nativeWebView.getTitle()
                    if (!title.isNullOrBlank()) {
                        state.pageTitle = title
                    }

                    navigator.canGoBack = nativeWebView.canGoBack()
                    navigator.canGoForward = nativeWebView.canGoForward()

                    delay(250.milliseconds)
                }
            }

            LaunchedEffect(it) {
                while (true) {
                    if (webViewJsBridge != null) {
                        for (raw in nativeWebView.drainIpcMessages()) {
                            parseJsMessage(raw)?.let { webViewJsBridge.dispatch(it) }
                        }
                    }
                    delay(50.milliseconds)
                }
            }
        }

        DisposableEffect(nativeWebView) {
            val listener: (String) -> Boolean = a@{
                if (navigator.requestInterceptor == null) {
                    return@a true
                }

                val webRequest = WebRequest(
                    url = it,
                    headers = mutableMapOf(),
                    isForMainFrame = true,
                    isRedirect = true
                )

                return@a when (val interceptResult =
                    navigator.requestInterceptor.onInterceptUrlRequest(webRequest, navigator)) {
                    WebRequestInterceptResult.Allow -> true

                    WebRequestInterceptResult.Reject -> false

                    is WebRequestInterceptResult.Modify -> {
                        interceptResult.request.let { modified ->
                            navigator.stopLoading()
                            navigator.loadUrl(modified.url, modified.headers)
                        }
                        false //no jump?
                    }
                }
            }
            nativeWebView.addNavigateListener(listener)
            onDispose {
                nativeWebView.removeNavigateListener(listener)
            }
        }

        if (LocalWebViewFactory.current != null) {
            Box(modifier) {
                LaunchedEffect(nativeWebView) {
                    onCreated(nativeWebView)
                }
            }
        } else {
            SwingPanel(
                modifier = modifier,
                factory = {
                    onCreated(nativeWebView)
                    nativeWebView
                }
            )
        }

        DisposableEffect(nativeWebView) {
            onDispose {
                state.webView = null
                webViewJsBridge?.webView = null
                currentOnDispose(nativeWebView)
            }
        }
    }
}

private fun Color.toRgba(): Rgba {
    val argb: Int = this.toArgb() // 0xAARRGGBB (sRGB)
    val a: UByte = ((argb ushr 24) and 0xFF).toUByte()
    val r: UByte = ((argb ushr 16) and 0xFF).toUByte()
    val g: UByte = ((argb ushr 8) and 0xFF).toUByte()
    val b: UByte = (argb and 0xFF).toUByte()
    return Rgba(r = r, g = g, b = b, a = a)
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
