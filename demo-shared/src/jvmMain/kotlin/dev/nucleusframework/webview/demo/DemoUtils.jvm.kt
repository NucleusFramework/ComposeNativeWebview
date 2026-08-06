package dev.nucleusframework.webview.demo

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
