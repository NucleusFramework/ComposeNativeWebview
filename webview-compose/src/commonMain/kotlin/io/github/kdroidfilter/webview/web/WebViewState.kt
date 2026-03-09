package io.github.kdroidfilter.webview.web

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import io.github.kdroidfilter.webview.cookie.CookieManager
import io.github.kdroidfilter.webview.cookie.WebViewCookieManager
import io.github.kdroidfilter.webview.setting.WebSettings

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
): WebViewState =
    remember {
        WebViewState(
            WebContent.Url(
                url = url,
                additionalHttpHeaders = additionalHttpHeaders,
            ),
        )
    }.apply {
        this.content =
            WebContent.Url(
                url = url,
                additionalHttpHeaders = additionalHttpHeaders,
            )
        extraSettings(this.webSettings)
    }

@Composable
fun rememberWebViewStateWithHTMLData(
    data: String,
    baseUrl: String? = null,
    encoding: String = "utf-8",
    mimeType: String? = null,
    historyUrl: String? = null,
): WebViewState =
    remember {
        WebViewState(WebContent.Data(data, baseUrl, encoding, mimeType, historyUrl))
    }.apply {
        this.content = WebContent.Data(data, baseUrl, encoding, mimeType, historyUrl)
    }

@Composable
fun rememberWebViewStateWithHTMLFile(
    fileName: String,
    readType: WebViewFileReadType,
): WebViewState =
    remember {
        WebViewState(WebContent.File(fileName, readType))
    }.apply {
        this.content = WebContent.File(fileName, readType)
    }
