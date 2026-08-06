package dev.nucleusframework.webview.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.nucleusframework.webview.request.RequestInterceptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Stable
class WebViewNavigator(
    val coroutineScope: CoroutineScope,
    val requestInterceptor: RequestInterceptor? = null,
) {
    private sealed interface NavigationEvent {
        data object Back : NavigationEvent
        data object Forward : NavigationEvent
        data object Reload : NavigationEvent
        data object StopLoading : NavigationEvent

        data class LoadUrl(
            val url: String,
            val additionalHttpHeaders: Map<String, String> = emptyMap(),
        ) : NavigationEvent

        data class LoadHtml(
            val html: String,
            val baseUrl: String? = null,
            val mimeType: String? = null,
            val encoding: String? = "utf-8",
            val historyUrl: String? = null,
        ) : NavigationEvent

        data class LoadHtmlFile(
            val fileName: String,
            val readType: WebViewFileReadType,
        ) : NavigationEvent

        data class EvaluateJavaScript(
            val script: String,
            val callback: ((String) -> Unit)? = null
        ) : NavigationEvent
    }

    private val navigationEvents: MutableSharedFlow<NavigationEvent> = MutableSharedFlow(replay = 1)

    internal suspend fun IWebView.handleNavigationEvents(): Nothing =
        withContext(Dispatchers.Main) {
            navigationEvents.collect { event ->
                when (event) {
                    NavigationEvent.Back -> goBack()
                    NavigationEvent.Forward -> goForward()
                    NavigationEvent.Reload -> reload()
                    NavigationEvent.StopLoading -> stopLoading()
                    is NavigationEvent.LoadUrl -> {
                        val normalizedUrl = normalizeHttpUrl(event.url)
                        val interceptor = requestInterceptor
                        if (interceptor == null) {
                            loadUrl(normalizedUrl, event.additionalHttpHeaders)
                        } else {
                            val request =
                                dev.nucleusframework.webview.request.WebRequest(
                                    url = normalizedUrl,
                                    headers = event.additionalHttpHeaders.toMutableMap(),
                                    isForMainFrame = true,
                                    method = "GET",
                                )
                            when (val result = interceptor.onInterceptUrlRequest(request, this@WebViewNavigator)) {
                                dev.nucleusframework.webview.request.WebRequestInterceptResult.Allow ->
                                    loadUrl(request.url, request.headers)

                                dev.nucleusframework.webview.request.WebRequestInterceptResult.Reject -> Unit

                                is dev.nucleusframework.webview.request.WebRequestInterceptResult.Modify ->
                                    loadUrl(result.request.url, result.request.headers)
                            }
                        }
                    }
                    is NavigationEvent.LoadHtml ->
                        loadHtml(
                            event.html,
                            event.baseUrl,
                            event.mimeType,
                            event.encoding,
                            event.historyUrl,
                        )

                    is NavigationEvent.LoadHtmlFile -> loadHtmlFile(event.fileName, event.readType)
                    is NavigationEvent.EvaluateJavaScript -> evaluateJavaScript(event.script, event.callback)
                }
            }
        }

    var canGoBack: Boolean by mutableStateOf(false)
        internal set

    var canGoForward: Boolean by mutableStateOf(false)
        internal set

    fun loadUrl(
        url: String,
        additionalHttpHeaders: Map<String, String> = emptyMap(),
    ) {
        coroutineScope.launch {
            navigationEvents.emit(NavigationEvent.LoadUrl(url, additionalHttpHeaders))
        }
    }

    fun loadHtml(
        html: String,
        baseUrl: String? = null,
        mimeType: String? = null,
        encoding: String? = "utf-8",
        historyUrl: String? = null,
    ) {
        coroutineScope.launch {
            navigationEvents.emit(NavigationEvent.LoadHtml(html, baseUrl, mimeType, encoding, historyUrl))
        }
    }

    fun loadHtmlFile(
        fileName: String,
        readType: WebViewFileReadType = WebViewFileReadType.ASSET_RESOURCES,
    ) {
        coroutineScope.launch {
            navigationEvents.emit(NavigationEvent.LoadHtmlFile(fileName, readType))
        }
    }

    fun evaluateJavaScript(
        script: String,
        callback: ((String) -> Unit)? = null
    ) {
        coroutineScope.launch { navigationEvents.emit(NavigationEvent.EvaluateJavaScript(script, callback)) }
    }

    fun navigateBack() {
        coroutineScope.launch { navigationEvents.emit(NavigationEvent.Back) }
    }

    fun navigateForward() {
        coroutineScope.launch { navigationEvents.emit(NavigationEvent.Forward) }
    }

    fun reload() {
        coroutineScope.launch { navigationEvents.emit(NavigationEvent.Reload) }
    }

    fun stopLoading() {
        coroutineScope.launch { navigationEvents.emit(NavigationEvent.StopLoading) }
    }
}

/**
 * Normalize bare-domain HTTP(S) URLs by appending a trailing slash,
 * matching browser behavior (e.g. https://example.com → https://example.com/).
 */
private fun normalizeHttpUrl(url: String): String {
    if (!url.startsWith("http://") && !url.startsWith("https://")) return url
    val schemeEnd = url.indexOf("://") + 3
    if (url.indexOf('/', schemeEnd) == -1 &&
        url.indexOf('?', schemeEnd) == -1 &&
        url.indexOf('#', schemeEnd) == -1
    ) {
        return "$url/"
    }
    return url
}

@Composable
fun rememberWebViewNavigator(
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    requestInterceptor: RequestInterceptor? = null,
): WebViewNavigator = remember(coroutineScope, requestInterceptor) { WebViewNavigator(coroutineScope, requestInterceptor) }
