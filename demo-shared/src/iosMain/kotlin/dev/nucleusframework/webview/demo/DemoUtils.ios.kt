package dev.nucleusframework.webview.demo

import platform.Foundation.NSCalendar
import platform.Foundation.NSDate
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitSecond
import platform.Foundation.NSCalendarUnitNanosecond

internal actual fun nowTimestamp(): String {
    val calendar = NSCalendar.currentCalendar
    val date = NSDate()
    val components = calendar.components(
        NSCalendarUnitHour or NSCalendarUnitMinute or NSCalendarUnitSecond or NSCalendarUnitNanosecond,
        date
    )
    val hour = components.hour.toInt()
    val minute = components.minute.toInt()
    val second = components.second.toInt()
    val millis = (components.nanosecond / 1_000_000).toInt()
    return buildString {
        append(hour.twoDigits())
        append(':')
        append(minute.twoDigits())
        append(':')
        append(second.twoDigits())
        append('.')
        append(millis.threeDigits())
    }
}
