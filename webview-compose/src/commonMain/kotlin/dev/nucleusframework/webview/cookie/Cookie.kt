package dev.nucleusframework.webview.cookie

/**
 * Cookie data class.
 */
data class Cookie(
    val name: String,
    val value: String,
    val domain: String? = null,
    val path: String? = null,
    val expiresDate: Long? = null,
    val isSessionOnly: Boolean = false,
    val sameSite: HTTPCookieSameSitePolicy? = null,
    val isSecure: Boolean? = null,
    val isHttpOnly: Boolean? = null,
    val maxAge: Long? = null,
) {
    enum class HTTPCookieSameSitePolicy {
        NONE,
        LAX,
        STRICT,
    }

    // Without buildString is empty in wasmJs
    override fun toString(): String = buildString {
        append("$name=$value")
        if (path != null) append("; Path=$path")
        // The domain must match the domain of the JavaScript origin. Setting cookies to foreign domains will be silently ignored.
        if (domain != null) append("; Domain=$domain")
        if (expiresDate != null) append("; Expires=" + getCookieExpirationDate(expiresDate))
        if (maxAge != null) append("; Max-Age=$maxAge")
        if (isSecure == true) append("; Secure")
        if (isHttpOnly == true) append("; HttpOnly")
        if (sameSite != null) append("; SameSite=$sameSite")
        append(';')
    }
}

expect fun getCookieExpirationDate(expiresDate: Long): String
