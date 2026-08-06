package dev.nucleusframework.webview.web.e2e

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.nucleusframework.webview.jsbridge.IJsMessageHandler
import dev.nucleusframework.webview.jsbridge.JsMessage
import dev.nucleusframework.webview.jsbridge.rememberWebViewJsBridge
import dev.nucleusframework.webview.request.RequestInterceptor
import dev.nucleusframework.webview.request.WebRequest
import dev.nucleusframework.webview.request.WebRequestInterceptResult
import dev.nucleusframework.webview.web.WebView
import dev.nucleusframework.webview.web.WebViewNavigator
import dev.nucleusframework.webview.web.rememberWebViewNavigator
import dev.nucleusframework.webview.web.rememberWebViewStateWithHTMLData
import java.util.concurrent.CopyOnWriteArrayList

@Composable
internal fun E2eHost(
    onFailure: (String) -> Unit,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(0) }

    val navigator = rememberWebViewNavigator(coroutineScope = scope)
    val state =
        rememberWebViewStateWithHTMLData(
            data = E2E_PAGE_HTML,
            baseUrl = "https://e2e.local/",
        )
    val jsBridge = rememberWebViewJsBridge(navigator)

    var rejectExample by remember { mutableStateOf(false) }
    val interceptor =
        remember {
            object : RequestInterceptor {
                override fun onInterceptUrlRequest(
                    request: WebRequest,
                    navigator: WebViewNavigator,
                ): WebRequestInterceptResult {
                    if (rejectExample && request.url.contains("example.com")) {
                        return WebRequestInterceptResult.Reject
                    }
                    return WebRequestInterceptResult.Allow
                }
            }
        }
    val interceptNavigator =
        rememberWebViewNavigator(coroutineScope = scope, requestInterceptor = interceptor)

    val bridgeHits = remember { CopyOnWriteArrayList<String>() }
    LaunchedEffect(jsBridge) {
        jsBridge.register(
            object : IJsMessageHandler {
                override fun methodName(): String = "e2eEcho"

                override fun handle(
                    message: JsMessage,
                    navigator: WebViewNavigator?,
                    callback: (String) -> Unit,
                ) {
                    bridgeHits += message.params
                    callback("""{"ok":true}""")
                }
            },
        )
    }

    when (phase) {
        0, 1, 2, 3, 4, 6, 7 -> {
            WebView(
                state = state,
                navigator = navigator,
                webViewJsBridge = jsBridge,
                modifier = Modifier.fillMaxSize(),
            )
        }
        5 -> {
            val interceptState =
                rememberWebViewStateWithHTMLData(
                    data = E2E_PAGE_HTML,
                    baseUrl = "https://e2e.local/",
                )
            WebView(
                state = interceptState,
                navigator = interceptNavigator,
                modifier = Modifier.fillMaxSize(),
            )
            InterceptorDriver(
                state = interceptState,
                navigator = interceptNavigator,
                rejectExample = { rejectExample = it },
                onFailure = onFailure,
                onDone = { phase = 6 },
            )
        }
    }

    when (phase) {
        0 -> LoadHtmlDriver(state, navigator, onFailure) { phase = 1 }
        1 -> EvaluateJsDriver(state, navigator, onFailure) { phase = 2 }
        2 -> NavigationDriver(state, navigator, onFailure) { phase = 3 }
        3 -> CookieDriver(state, onFailure) { phase = 4 }
        4 -> JsBridgeDriver(state, navigator, bridgeHits, onFailure) { phase = 5 }
        6 -> ScreenshotDriver(state, onFailure) { phase = 7 }
        7 -> UserAgentDriver(onFailure, onDone)
    }
}
