package com.tgt.lists.atlas.api.util

import com.tgt.lists.atlas.api.util.Constants.DATE_TIME_PATTERN
import mu.KotlinLogging
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.*

fun convertStringToDate(date: String?): Date? {
    val log = KotlinLogging.logger {}

    if (date.isNullOrEmpty()) { return null }
    return try { SimpleDateFormat(DATE_TIME_PATTERN).parse(date) } catch (e: ParseException) {
        log.debug("Exception parsing date [date: $date] with [error: ${e.message}]", e)
        null
    }
}

fun getLocalInstant(): Instant {
    return LocalDateTime.now().toInstant(ZoneOffset.UTC)
}

fun getLocalDate(instant: Instant?): LocalDate? {
    return instant?.let { LocalDate.ofInstant(it, ZoneId.of("UTC")) }
}

fun getLocalDateTime(instant: Instant?): LocalDateTime? {
    return instant?.let { LocalDateTime.ofInstant(it, ZoneId.of("UTC")) }
}

fun getLocalDateTimeFromInstant(instant: Instant?): String? {
    return instant?.let { "${LocalDateTime.ofInstant(it, ZoneOffset.UTC).withNano(0)}Z" }
}

fun getExpirationDate(now: Instant, expirationDays: Long): LocalDate {
    return LocalDate.ofInstant(now.plus(expirationDays, ChronoUnit.DAYS), ZoneOffset.UTC)
}

fun addZ(str: LocalDateTime?): String? {
    if (str == null) {
        return null
    }
    return str.toString() + "Z"
}