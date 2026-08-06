package dev.nucleusframework.webview.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.nucleusframework.webview.util.KLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLIFrameElement

/**
 * HtmlViewNavigator provides navigation functionality for HtmlView
 */
class HtmlViewNavigator(
    private val coroutineScope: CoroutineScope,
) {
    private val navigationEvents = MutableSharedFlow<NavigationEvent>()

    var canGoBack by mutableStateOf(false)
        internal set

    var canGoForward by mutableStateOf(false)
        internal set

    /**
     * Handle navigation events for the given HTML element
     */
    internal suspend fun handleNavigationEvents(element: HTMLIFrameElement) {
        navigationEvents.collect { event ->
            when (event) {
                is NavigationEvent.Back -> element.contentWindow?.history?.back()
                is NavigationEvent.Forward -> element.contentWindow?.history?.forward()
                is NavigationEvent.Reload -> element.contentWindow?.location?.reload()
                is NavigationEvent.LoadUrl -> element.src = event.url
                is NavigationEvent.LoadHtml -> element.srcdoc = event.data
                is NavigationEvent.EvaluateJavaScript -> {
                    try {
                        val result = evaluateScriptJs(element, event.script)
                        event.callback?.invoke(result)
                    } catch (t: Throwable) {
                        event.callback?.invoke("Error: ${t.message}")
                    }
                }
                is NavigationEvent.StopLoading -> element.contentWindow?.stop()
            }
            updateNavigationState(element)
        }
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

    fun loadUrl(
        url: String,
        additionalHttpHeaders: Map<String, String> = emptyMap(),
    ) {
        coroutineScope.launch {
            navigationEvents.emit(NavigationEvent.LoadUrl(url, additionalHttpHeaders))
        }
    }

    fun loadHtml(
        data: String,
        baseUrl: String? = null,
        mimeType: String? = null,
        encoding: String? = "utf-8",
        historyUrl: String? = null,
    ) {
        coroutineScope.launch {
            navigationEvents.emit(
                NavigationEvent.LoadHtml(
                    data = data,
                    baseUrl = baseUrl,
                    mimeType = mimeType,
                    encoding = encoding,
                    historyUrl = historyUrl
                )
            )
        }
    }

    fun evaluateJavaScript(
        script: String,
        callback: ((String) -> Unit)? = null,
    ) {
        coroutineScope.launch {
            navigationEvents.emit(NavigationEvent.EvaluateJavaScript(script, callback))
        }
    }

    private fun updateNavigationState(element: HTMLIFrameElement) {
        try {
            canGoBack = element.contentWindow?.history?.length?.let {
                it > 1
            } ?: false
            // Browser iframe history API does not expose forward availability
            canGoForward = false
        } catch (e: Exception) {
            KLogger.e(
                t = e,
                tag = "HtmlViewNavigator"
            ) {
                "Error updating navigation state"
            }
        }
    }

    private sealed class NavigationEvent {
        data object Back : NavigationEvent()

        data object Forward : NavigationEvent()

        data object Reload : NavigationEvent()

        data object StopLoading : NavigationEvent()

        data class LoadUrl(
            val url: String,
            val additionalHttpHeaders: Map<String, String>,
        ) : NavigationEvent()

        data class LoadHtml(
            val data: String,
            val baseUrl: String?,
            val mimeType: String?,
            val encoding: String?,
            val historyUrl: String?,
        ) : NavigationEvent()

        data class EvaluateJavaScript(
            val script: String,
            val callback: ((String) -> Unit)?,
        ) : NavigationEvent()
    }
}

/**
 * Create and remember an HtmlViewNavigator instance
 */
@Composable
fun rememberHtmlViewNavigator(): HtmlViewNavigator {
    val scope = rememberCoroutineScope()
    return remember { HtmlViewNavigator(scope) }
}
