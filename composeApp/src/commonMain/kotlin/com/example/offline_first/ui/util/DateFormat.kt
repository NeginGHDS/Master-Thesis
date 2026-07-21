package com.example.offline_first.ui.util

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime

private fun Int.pad() = if (this < 10) "0$this" else "$this"

@OptIn(ExperimentalTime::class)
fun formatEpochMillis(epochMillis: Long): String {
    val dateTime = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${dateTime.year}-${dateTime.monthNumber.pad()}-${dateTime.dayOfMonth.pad()} " +
        "${dateTime.hour.pad()}:${dateTime.minute.pad()}"
}
