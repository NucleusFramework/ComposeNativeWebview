package dev.nucleusframework.webview.e2e

import dev.nucleusframework.webview.request.RequestInterceptor
import dev.nucleusframework.webview.request.WebRequest
import dev.nucleusframework.webview.request.WebRequestInterceptResult
import dev.nucleusframework.webview.web.WebViewNavigator

internal class E2eRequestInterceptor(
    private val enabled: () -> Boolean,
    private val onLog: (String) -> Unit,
) : RequestInterceptor {
    override fun onInterceptUrlRequest(
        request: WebRequest,
        navigator: WebViewNavigator,
    ): WebRequestInterceptResult {
        if (!enabled()) return WebRequestInterceptResult.Allow

        val url = request.url
        if (url.contains("blocked", ignoreCase = true)) {
            onLog("interceptor: reject url=$url")
            return WebRequestInterceptResult.Reject
        }

        if (url == "https://example.com" || url == "https://www.example.com") {
            val rewritten = "https://httpbin.org/anything?from=interceptor&original=" + uriEncodeComponent(url)
            onLog("interceptor: rewrite $url -> $rewritten")
            val modified =
                request.copy(
                    url = rewritten,
                    headers = request.headers.toMutableMap().apply { put("X-Intercepted", "true") },
                )
            return WebRequestInterceptResult.Modify(modified)
        }

        request.headers["X-Intercepted"] = "true"
        onLog("interceptor: allow url=$url")
        return WebRequestInterceptResult.Allow
    }
}
