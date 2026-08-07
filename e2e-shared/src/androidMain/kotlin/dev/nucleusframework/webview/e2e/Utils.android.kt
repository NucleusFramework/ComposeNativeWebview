package dev.nucleusframework.webview.e2e

import java.time.LocalTime

internal actual fun nowTimestamp(): String {
    val time = LocalTime.now()
    return buildString {
        append(time.hour.twoDigits())
        append(':')
        append(time.minute.twoDigits())
        append(':')
        append(time.second.twoDigits())
        append('.')
        append((time.nano / 1_000_000).threeDigits())
    }
}

internal actual fun currentTimeMillis(): Long = System.currentTimeMillis()
