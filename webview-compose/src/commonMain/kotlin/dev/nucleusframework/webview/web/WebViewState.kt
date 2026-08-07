package dev.nucleusframework.webview.web

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import dev.nucleusframework.webview.cookie.CookieManager
import dev.nucleusframework.webview.cookie.WebViewCookieManager
import dev.nucleusframework.webview.setting.WebSettings

@Stable
class WebViewState(
    webContent: WebContent,
) {
    var lastLoadedUrl: String? by mutableStateOf(null)
        internal set

    var content: WebContent by mutableStateOf(webContent)

    var loadingState: LoadingState by mutableStateOf(LoadingState.Initializing)
        internal set

    val isLoading: Boolean
        get() = loadingState !is LoadingState.Finished

    var pageTitle: String? by mutableStateOf(null)
        internal set

    val errorsForCurrentRequest: SnapshotStateList<WebViewError> = mutableStateListOf()

    val webSettings: WebSettings by mutableStateOf(WebSettings())

    var webView: IWebView? by mutableStateOf(null)
        internal set

    val cookieManager: CookieManager by mutableStateOf(WebViewCookieManager())
}

@Composable
fun rememberWebViewState(
    url: String,
    additionalHttpHeaders: Map<String, String> = emptyMap(),
    extraSettings: WebSettings.() -> Unit = {},
): WebViewState {
    val state =
        remember {
            WebViewState(WebContent.Url(url, additionalHttpHeaders))
        }
    // Sync only when caller inputs change. Assigning on every recomposition (the old
    // `.apply { content = … }` pattern) clobbers programmatic `state.content` updates
    // (e.g. visual suite C09) as soon as loadingState triggers a parent recompose.
    LaunchedEffect(url, additionalHttpHeaders) {
        state.content = WebContent.Url(url, additionalHttpHeaders)
    }
    SideEffect {
        extraSettings(state.webSettings)
    }
    return state
}

@Composable
fun rememberWebViewStateWithHTMLData(
    data: String,
    baseUrl: String? = null,
    encoding: String = "utf-8",
    mimeType: String? = null,
    historyUrl: String? = null,
): WebViewState {
    val state =
        remember {
            WebViewState(WebContent.Data(data, baseUrl, encoding, mimeType, historyUrl))
        }
    LaunchedEffect(data, baseUrl, encoding, mimeType, historyUrl) {
        state.content = WebContent.Data(data, baseUrl, encoding, mimeType, historyUrl)
    }
    return state
}

@Composable
fun rememberWebViewStateWithHTMLFile(
    fileName: String,
    readType: WebViewFileReadType,
): WebViewState {
    val state =
        remember {
            WebViewState(WebContent.File(fileName, readType))
        }
    LaunchedEffect(fileName, readType) {
        state.content = WebContent.File(fileName, readType)
    }
    return state
}
