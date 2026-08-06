package dev.nucleusframework.webview.demo

internal fun normalizeUrl(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return "about:blank"
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
    if (trimmed.startsWith("file://")) return trimmed
    return "https://$trimmed"
}

internal fun hostFromUrl(url: String): String? {
    val trimmed = url.trim()
    val schemeSeparator = "://"
    val schemeIndex = trimmed.indexOf(schemeSeparator)
    if (schemeIndex <= 0) return null

    val scheme = trimmed.substring(0, schemeIndex).lowercase()
    if (scheme != "http" && scheme != "https") return null

    val authorityStart = schemeIndex + schemeSeparator.length
    if (authorityStart >= trimmed.length) return null

    val authority =
        trimmed
            .substring(authorityStart)
            .takeWhile { it != '/' && it != '?' && it != '#' }
            .substringAfterLast('@')
            .trim()
    if (authority.isEmpty()) return null

    if (authority.startsWith("[")) {
        val end = authority.indexOf(']')
        if (end > 1) return authority.substring(1, end)
    }

    return authority.substringBefore(':').takeIf { it.isNotBlank() }
}

internal expect fun nowTimestamp(): String

internal fun Int.twoDigits(): String = toString().padStart(2, '0')

internal fun Int.threeDigits(): String = toString().padStart(3, '0')

internal fun uriEncodeComponent(value: String): String {
    val bytes = value.encodeToByteArray()
    val out = StringBuilder(bytes.size)
    for (b in bytes) {
        val c = b.toInt() and 0xFF
        val ch = c.toChar()
        val unreserved =
            (ch in 'a'..'z') ||
                (ch in 'A'..'Z') ||
                (ch in '0'..'9') ||
                ch == '-' || ch == '_' || ch == '.' || ch == '~'
        if (unreserved) {
            out.append(ch)
        } else {
            out.append('%')
            out.append(c.toString(16).uppercase().padStart(2, '0'))
        }
    }
    return out.toString()
}
