package dev.nucleusframework.webview.web

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import dev.nucleusframework.webview.jsbridge.WebViewJsBridge
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.merge

val LocalWebViewFactory = staticCompositionLocalOf<((WebViewFactoryParam) -> NativeWebView)?> { null }

/**
 * Multiplatform WebView composable.
 *
 * @param content Compose UI drawn **over** the embedded native WebView
 * (same role as [dev.nucleusframework.window.tao.NativeView]'s content slot).
 * On desktop this is required for overlays above the native surface; on
 * Android / iOS / Wasm it is a regular Compose sibling layered on top.
 */
@Composable
fun WebView(
    state: WebViewState,
    modifier: Modifier = Modifier,
    navigator: WebViewNavigator = rememberWebViewNavigator(),
    webViewJsBridge: WebViewJsBridge? = null,
    onCreated: (NativeWebView) -> Unit = {},
    onDispose: (NativeWebView) -> Unit = {},
    content: @Composable () -> Unit = {},
) {
    val factory = LocalWebViewFactory.current ?: ::defaultWebViewFactory

    val webView = state.webView

    webView?.let { wv ->
        LaunchedEffect(wv, navigator) {
            with(navigator) {
                wv.handleNavigationEvents()
            }
        }

        LaunchedEffect(wv, state) {
            snapshotFlow { state.content }.collect { pageContent ->
                wv.loadContent(pageContent)
            }
        }

        if (webViewJsBridge != null) {
            LaunchedEffect(wv, state) {
                val loadingStateFlow =
                    snapshotFlow { state.loadingState }.filterIsInstance<LoadingState.Finished>()
                val lastLoadedUrlFlow =
                    snapshotFlow { state.lastLoadedUrl }.filter { !it.isNullOrEmpty() }

                // Inject on Finished *or* URL change. Gating only on Finished misses
                // navigations that never leave Finished in the Compose poller
                // (e.g. very fast data: loads on WebView2).
                merge(loadingStateFlow, lastLoadedUrlFlow).collect {
                    wv.injectJsBridge()
                }
            }
        }
    }

    ActualWebView(
        state = state,
        modifier = modifier,
        navigator = navigator,
        webViewJsBridge = webViewJsBridge,
        onCreated = onCreated,
        onDispose = onDispose,
        factory = factory,
        content = content,
    )

    DisposableEffect(Unit) {
        onDispose { webViewJsBridge?.clear() }
    }
}

expect class WebViewFactoryParam

expect fun defaultWebViewFactory(param: WebViewFactoryParam): NativeWebView

@Composable
expect fun ActualWebView(
    state: WebViewState,
    modifier: Modifier = Modifier,
    navigator: WebViewNavigator = rememberWebViewNavigator(),
    webViewJsBridge: WebViewJsBridge? = null,
    onCreated: (NativeWebView) -> Unit = {},
    onDispose: (NativeWebView) -> Unit = {},
    factory: (WebViewFactoryParam) -> NativeWebView = ::defaultWebViewFactory,
    content: @Composable () -> Unit = {},
)
