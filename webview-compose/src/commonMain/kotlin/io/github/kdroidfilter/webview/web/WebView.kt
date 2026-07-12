package io.github.kdroidfilter.webview.web

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import io.github.kdroidfilter.webview.jsbridge.WebViewJsBridge
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.merge

val LocalWebViewFactory = staticCompositionLocalOf<((WebViewFactoryParam) -> NativeWebView)?> { null }

@Composable
fun WebView(
    state: WebViewState,
    modifier: Modifier = Modifier,
    navigator: WebViewNavigator = rememberWebViewNavigator(),
    webViewJsBridge: WebViewJsBridge? = null,
    onCreated: (NativeWebView) -> Unit = {},
    onDispose: (NativeWebView) -> Unit = {}
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
            snapshotFlow { state.content }.collect { content ->
                wv.loadContent(content)
            }
        }

        if (webViewJsBridge != null) {
            LaunchedEffect(wv, state) {
                val loadingStateFlow =
                    snapshotFlow { state.loadingState }.filterIsInstance<LoadingState.Finished>()
                val lastLoadedUrlFlow =
                    snapshotFlow { state.lastLoadedUrl }.filter { !it.isNullOrEmpty() }

                merge(loadingStateFlow, lastLoadedUrlFlow).collect {
                    if (state.loadingState is LoadingState.Finished) {
                        wv.injectJsBridge()
                    }
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
        factory = factory
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
)
