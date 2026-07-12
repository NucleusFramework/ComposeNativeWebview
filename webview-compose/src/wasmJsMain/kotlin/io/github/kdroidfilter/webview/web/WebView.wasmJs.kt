@file:OptIn(ExperimentalWasmJsInterop::class)
package io.github.kdroidfilter.webview.web

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import io.github.kdroidfilter.webview.jsbridge.WebViewJsBridge
import io.github.kdroidfilter.webview.jsbridge.parseJsMessage
import io.github.kdroidfilter.webview.setting.WebSettings
import io.github.kdroidfilter.webview.util.KLogger
import kotlinx.browser.document
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLIFrameElement
import org.w3c.dom.MessageEvent
import org.w3c.dom.Node
import org.w3c.dom.events.Event

/**
 * Platform-specific parameters for the WebView factory in WebAssembly/JavaScript.
 */
actual class WebViewFactoryParam {
    var container: Node = document.body?.shadowRoot ?: document.body!!
    var existingElement: HTMLIFrameElement? = null
}

/**
 * Default factory function for creating a WebView on the WebAssembly/JavaScript platform.
 */
actual fun defaultWebViewFactory(param: WebViewFactoryParam): NativeWebView {
    val iframe = param.existingElement ?: document.createElement("iframe") as HTMLIFrameElement

    iframe.style.apply {
        border = "none"
        width = "100%"
        height = "100%"
    }

    return NativeWebView(iframe)
}

/**
 * Factory function for creating a WebView with WebSettings applied.
 */
fun createWebViewWithSettings(
    param: WebViewFactoryParam,
    settings: WebSettings,
): NativeWebView {
    val iframe = param.existingElement ?: document.createElement("iframe") as HTMLIFrameElement
    val wasmSettings = settings.wasmJSWebSettings

    iframe.style.apply {
        width = "100%"
        height = "100%"

        border = if (wasmSettings.showBorder) {
            wasmSettings.borderStyle
        } else {
            "none"
        }

        val bgColor = (wasmSettings.backgroundColor ?: settings.backgroundColor)
        if (bgColor != androidx.compose.ui.graphics.Color.Transparent) {
            backgroundColor = "#${bgColor.value.toString(16).padStart(8, '0').substring(2)}"
        }

        wasmSettings.customContainerStyle?.let { customStyle ->
            cssText += "; $customStyle"
        }
    }

    if (wasmSettings.enableSandbox) {
        iframe.setAttribute(
            qualifiedName = "sandbox",
            value = wasmSettings.sandboxPermissions
        )
    }

    if (wasmSettings.allowFullscreen) {
        iframe.setAttribute(
            qualifiedName = "allowfullscreen",
            value = "true"
        )
    }

    return NativeWebView(iframe)
}

/**
 * Implementation of the WebView composable for the WebAssembly/JavaScript platform.
 */
@Composable
actual fun ActualWebView(
    state: WebViewState,
    modifier: Modifier,
    navigator: WebViewNavigator,
    webViewJsBridge: WebViewJsBridge?,
    onCreated: (NativeWebView) -> Unit,
    onDispose: (NativeWebView) -> Unit,
    factory: (WebViewFactoryParam) -> NativeWebView
) {
    val scope = rememberCoroutineScope()
    val htmlNavigator = rememberHtmlViewNavigator()
    val htmlViewState = remember { HtmlViewState() }
    val bridgeCleanup = remember { mutableStateOf<(() -> Unit)?>(null) }

    // Reactively sync navigation state from htmlNavigator to navigator
    LaunchedEffect(navigator, htmlNavigator) {
        snapshotFlow { htmlNavigator.canGoBack to htmlNavigator.canGoForward }
            .collect { (canGoBack, canGoForward) ->
                navigator.canGoBack = canGoBack
                navigator.canGoForward = canGoForward
            }
    }

    LaunchedEffect(state.content) {
        when (state.content) {
            is WebContent.Url -> {
                htmlViewState.content = HtmlContent.Url(
                    (state.content as WebContent.Url).url,
                    (state.content as WebContent.Url).additionalHttpHeaders,
                )
            }

            is WebContent.Data -> {
                val data = (state.content as WebContent.Data).data
                val htmlWithBridge = if (webViewJsBridge != null) {
                    injectJsBridgeToHtml(data, webViewJsBridge.jsBridgeName)
                } else {
                    data
                }

                htmlViewState.content = HtmlContent.Data(
                    data = htmlWithBridge,
                    baseUrl = (state.content as WebContent.Data).baseUrl
                )
            }

            is WebContent.File -> {
                val fileName = (state.content as WebContent.File).fileName
                val fileReadType = (state.content as WebContent.File).readType

                val webView = state.webView
                if (webView != null) {
                    webView.loadHtmlFile(fileName, fileReadType)
                } else {
                    htmlViewState.loadingState = HtmlLoadingState.Loading
                }
            }

            is WebContent.NavigatorOnly -> {
                // No action needed
            }
        }
    }

    // Sync HtmlViewState → WebViewState using snapshotFlow to avoid missing intermediate states
    LaunchedEffect(htmlViewState, state) {
        snapshotFlow {
            Triple(htmlViewState.lastLoadedUrl, htmlViewState.pageTitle, htmlViewState.loadingState)
        }.collect { (lastLoadedUrl, pageTitle, loadingState) ->
            lastLoadedUrl?.let { state.lastLoadedUrl = it }
            pageTitle?.let { state.pageTitle = it }

            when (loadingState) {
                is HtmlLoadingState.Loading -> {
                    // Simulate progress like desktop: iframe doesn't provide real progress,
                    // so we animate from 0.1 to 0.9 while loading
                    state.loadingState = LoadingState.Loading(0.1f)
                    scope.launch {
                        while (htmlViewState.loadingState is HtmlLoadingState.Loading) {
                            kotlinx.coroutines.delay(100)
                            val current = state.loadingState
                            if (current is LoadingState.Loading) {
                                state.loadingState = LoadingState.Loading(
                                    (current.progress + 0.02f).coerceAtMost(0.9f)
                                )
                            }
                        }
                    }
                }

                is HtmlLoadingState.Finished -> {
                    state.loadingState = LoadingState.Finished
                    state.webView?.nativeWebView?.element?.let { element ->
                        try {
                            state.pageTitle = evaluateScriptJs(
                                element,
                                "document.title"
                            )
                            state.lastLoadedUrl = evaluateScriptJs(
                                element,
                                "document.location"
                            )
                        } catch (t: Throwable) {
                            KLogger.e(
                                t = t,
                                tag = "ActualWebView"
                            ) {
                                "Error getting document from iframe: ${t.message}"
                            }
                        }
                    }
                }

                is HtmlLoadingState.Initializing -> state.loadingState = LoadingState.Initializing
            }
        }
    }

    if (LocalWebViewFactory.current != null) {
        Box(modifier) {
            val scope = rememberCoroutineScope()
            remember(state) {
                // Create a dummy iframe for testing
                val element = document.createElement("iframe") as HTMLIFrameElement
                val nativeWebView = factory(
                    WebViewFactoryParam().apply {
                        existingElement = element
                    }
                )

                val webViewWrapper = WasmJsWebView(
                    element = element,
                    nativeWebView = nativeWebView,
                    scope = scope,
                    webViewJsBridge = webViewJsBridge,
                    onLoadStarted = { htmlViewState.loadingState = HtmlLoadingState.Loading },
                )

                state.webView = webViewWrapper
                onCreated(nativeWebView)
            }
        }
    } else {
        HtmlView(
            state = htmlViewState,
            modifier = modifier,
            navigator = htmlNavigator,
            onCreated = { element ->
                val nativeWebView = if (
                    state.webSettings.wasmJSWebSettings.let {
                        it.backgroundColor != null ||
                                it.showBorder ||
                                it.enableSandbox ||
                                it.customContainerStyle != null
                    }
                ) {
                    createWebViewWithSettings(
                        WebViewFactoryParam().apply {
                            existingElement = element
                        },
                        state.webSettings
                    )
                } else {
                    factory(
                        WebViewFactoryParam().apply {
                            existingElement = element
                        }
                    )
                }

                val webViewWrapper = WasmJsWebView(
                    element = element,
                    nativeWebView = nativeWebView,
                    scope = scope,
                    webViewJsBridge = webViewJsBridge,
                    onLoadStarted = { htmlViewState.loadingState = HtmlLoadingState.Loading },
                )

                state.webView = webViewWrapper

                if (webViewJsBridge != null) {
                    bridgeCleanup.value = setupJsBridgeForWasm(element, webViewJsBridge, webViewWrapper)
                }

                if (state.content is WebContent.File) {
                    val fileName = (state.content as WebContent.File).fileName
                    val readType = (state.content as WebContent.File).readType
                    scope.launch {
                        webViewWrapper.loadHtmlFile(fileName, readType)
                    }
                }

                onCreated(nativeWebView)
            },
            onDispose = { element ->
                bridgeCleanup.value?.invoke()
                bridgeCleanup.value = null
                state.webView?.let {
                    onDispose(NativeWebView(element))
                    state.webView = null
                }
            }
        )
    }
}

/**
 * Set up the JavaScript bridge for WasmJS platform.
 * Returns a cleanup function that removes the message listener.
 */
private fun setupJsBridgeForWasm(
    element: HTMLIFrameElement,
    webViewJsBridge: WebViewJsBridge,
    webViewWrapper: WasmJsWebView
): () -> Unit {
    val messageHandler: (Event) -> Unit = { event ->
        val messageEvent = event as MessageEvent

        if (
            messageEvent.source == element.contentWindow &&
            messageEvent.data != null
        ) {
            try {
                parseJsMessage(
                    raw = messageEvent.data.toString(),
                    expectedType = webViewJsBridge.jsBridgeName,
                )?.let(webViewJsBridge::dispatch)
            } catch (e: Exception) {
                KLogger.e(
                    t = e,
                    tag = "WasmJsWebView"
                ) {
                    "Error processing message: ${e.message}"
                }
            }
        }
    }

    kotlinx.browser.window.addEventListener("message", messageHandler)
    webViewJsBridge.webView = webViewWrapper

    return { kotlinx.browser.window.removeEventListener("message", messageHandler) }
}
