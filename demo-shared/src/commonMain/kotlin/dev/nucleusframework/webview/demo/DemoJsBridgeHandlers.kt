package dev.nucleusframework.webview.demo

import dev.nucleusframework.webview.cookie.Cookie
import dev.nucleusframework.webview.cookie.CookieManager
import dev.nucleusframework.webview.jsbridge.IJsMessageHandler
import dev.nucleusframework.webview.jsbridge.JsMessage
import dev.nucleusframework.webview.web.WebViewNavigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val demoJson = Json { ignoreUnknownKeys = true }

private fun jsonStringField(
    raw: String,
    key: String,
): String? =
    runCatching {
        demoJson.parseToJsonElement(raw).jsonObject[key]?.jsonPrimitive?.content
    }.getOrNull()

internal class EchoHandler(
    private val onLog: (String) -> Unit,
) : IJsMessageHandler {
    override fun methodName(): String = "echo"

    override fun handle(
        message: JsMessage,
        navigator: WebViewNavigator?,
        callback: (String) -> Unit,
    ) {
        val text = jsonStringField(message.params, "text") ?: message.params
        onLog("jsbridge: echo text=${text.take(120)}")
        callback(text)
    }
}

internal class AppInfoHandler(
    private val onLog: (String) -> Unit,
) : IJsMessageHandler {
    override fun methodName(): String = "appInfo"

    override fun handle(
        message: JsMessage,
        navigator: WebViewNavigator?,
        callback: (String) -> Unit,
    ) {
        onLog("jsbridge: appInfo")
        callback(platformInfoJson())
    }
}

internal class NavigateHandler(
    private val onLog: (String) -> Unit,
) : IJsMessageHandler {
    override fun methodName(): String = "navigate"

    override fun handle(
        message: JsMessage,
        navigator: WebViewNavigator?,
        callback: (String) -> Unit,
    ) {
        val url = normalizeUrl(jsonStringField(message.params, "url") ?: "")
        onLog("jsbridge: navigate url=$url")
        navigator?.loadUrl(url)
        callback("ok")
    }
}

internal class SetCookieHandler(
    private val scope: CoroutineScope,
    private val cookieManager: CookieManager,
    private val onLog: (String) -> Unit,
) : IJsMessageHandler {
    override fun methodName(): String = "setCookie"

    override fun handle(
        message: JsMessage,
        navigator: WebViewNavigator?,
        callback: (String) -> Unit,
    ) {
        val url = normalizeUrl(jsonStringField(message.params, "url") ?: "")
        val name = jsonStringField(message.params, "name") ?: "demo_cookie"
        val value = jsonStringField(message.params, "value") ?: "from_js"

        val cookie =
            Cookie(
                name = name,
                value = value,
                domain = hostFromUrl(url),
                path = "/",
                isSessionOnly = true,
                isSecure = url.startsWith("https://"),
                isHttpOnly = false,
                sameSite = Cookie.HTTPCookieSameSitePolicy.LAX,
            )

        scope.launch {
            cookieManager.setCookie(url, cookie)
            onLog("jsbridge: setCookie url=$url name=$name")
            callback("ok")
        }
    }
}

internal class GetCookiesHandler(
    private val scope: CoroutineScope,
    private val cookieManager: CookieManager,
    private val onLog: (String) -> Unit,
) : IJsMessageHandler {
    override fun methodName(): String = "getCookies"

    override fun handle(
        message: JsMessage,
        navigator: WebViewNavigator?,
        callback: (String) -> Unit,
    ) {
        val url = normalizeUrl(jsonStringField(message.params, "url") ?: "")
        scope.launch {
            val cookies = cookieManager.getCookies(url)
            onLog("jsbridge: getCookies url=$url count=${cookies.size}")
            callback(cookies.joinToString(prefix = "[", postfix = "]") { it.toString() })
        }
    }
}

internal class CustomHandler(
    private val onLog: (String) -> Unit,
) : IJsMessageHandler {
    override fun methodName(): String = "custom"

    override fun handle(
        message: JsMessage,
        navigator: WebViewNavigator?,
        callback: (String) -> Unit,
    ) {
        onLog("jsbridge: custom params=${message.params.take(160)}")
        callback(message.params)
    }
}

