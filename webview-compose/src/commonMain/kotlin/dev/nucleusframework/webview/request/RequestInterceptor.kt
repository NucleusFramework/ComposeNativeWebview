package dev.nucleusframework.webview.request

import dev.nucleusframework.webview.web.WebViewNavigator

interface RequestInterceptor {
    fun onInterceptUrlRequest(
        request: WebRequest,
        navigator: WebViewNavigator,
    ): WebRequestInterceptResult
}

