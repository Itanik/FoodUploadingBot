import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals

fun main() {
    val timeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")
    val string =
        LocalDateTime.ofInstant(ZonedDateTime.now().toInstant(), ZoneId.of("Europe/Moscow")).format(timeFormatter)
    println(string)
    val date = LocalDateTime.from(timeFormatter.parse(string)).format(timeFormatter)
    println(date.toString())
    assertEquals(string, date.toString())
}