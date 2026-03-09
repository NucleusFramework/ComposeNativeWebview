package io.github.kdroidfilter.webview.setting

sealed class ProxyConfig {
    abstract val host: String
    abstract val port: Int

    data class Http(
        override val host: String,
        override val port: Int
    ) : ProxyConfig()

    data class Socks5(
        override val host: String,
        override val port: Int
    ) : ProxyConfig()

    override fun toString() = "$host:$port"
}
