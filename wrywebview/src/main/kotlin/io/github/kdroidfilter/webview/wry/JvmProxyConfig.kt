package io.github.kdroidfilter.webview.wry

/**
 * JVM-specific proxy config
 */
sealed class JvmProxyConfig {
    abstract val host: String
    abstract val port: Int

    data class Http(
        override val host: String,
        override val port: Int
    ) : JvmProxyConfig() {
        override fun toProxy(): Proxy.Http = Proxy.Http(Address(host, port.toUShort()))
    }

    data class Socks5(
        override val host: String,
        override val port: Int
    ) : JvmProxyConfig() {
        override fun toProxy(): Proxy.Socks5 = Proxy.Socks5(Address(host, port.toUShort()))
    }

    internal abstract fun toProxy(): Proxy
}
