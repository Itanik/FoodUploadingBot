package misc

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

fun Calendar.toLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(this.toInstant(), this.timeZone.toZoneId())

fun LocalDateTime.defaultFormat(): String = format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)