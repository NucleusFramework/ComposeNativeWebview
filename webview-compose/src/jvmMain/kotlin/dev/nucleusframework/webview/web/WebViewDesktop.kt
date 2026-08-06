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
import dev.nucleusframework.webview.jsbridge.WebViewJsBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

actual class WebViewFactoryParam(
    val state: WebViewState,
    val fileContent: String = "",
)

actual fun defaultWebViewFactory(
    param: WebViewFactoryParam
): NativeWebView = NativeWebView()

/**
 * Desktop WebView composable (no-op).
 *
 * Renders an empty [Box] and wires [WebViewState] / navigator hooks so the
 * shared API remains usable while desktop has no native backend.
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
        // Mark as finished immediately: nothing is loading on a no-op backend.
        state.loadingState = LoadingState.Finished
        navigator.canGoBack = false
        navigator.canGoForward = false
    }

    Box(modifier) {
        LaunchedEffect(nativeWebView) {
            onCreated(nativeWebView)
        }
    }

    DisposableEffect(nativeWebView) {
        onDispose {
            state.webView = null
            webViewJsBridge?.webView = null
            currentOnDispose(nativeWebView)
            nativeWebView.destroy()
        }
    }
}

/**
 * Captures a screenshot of the WebView and returns it as a [BufferedImage].
 * Always returns null on the desktop no-op backend unless a custom
 * [NativeWebView] override provides image bytes.
 */
suspend fun IWebView.toAwtImage(): BufferedImage? {
    val bytes = captureScreenshotOrNull() ?: return null
    return withContext(Dispatchers.IO) {
        ImageIO.read(ByteArrayInputStream(bytes))
    }
}
