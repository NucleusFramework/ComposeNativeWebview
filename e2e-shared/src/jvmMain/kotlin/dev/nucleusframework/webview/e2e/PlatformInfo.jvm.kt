package dev.nucleusframework.webview.e2e

internal actual fun platformInfoJson(): String {
    val os = System.getProperty("os.name").orEmpty()
    val arch = System.getProperty("os.arch").orEmpty()
    val java = System.getProperty("java.version").orEmpty()
    return """{"platform":"desktop","os":"$os","arch":"$arch","runtime":"java","runtimeVersion":"$java"}"""
}
