package com.diprish.utilitymeter.ui

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

private val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
private val dateTimeFormat = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())

fun formatDate(timestamp: Long): String = dateFormat.format(Date(timestamp))

fun formatDateTime(timestamp: Long): String = dateTimeFormat.format(Date(timestamp))

/** Trim trailing zeros so 12.50 -> "12.5" and 12.0 -> "12". */
fun formatNumber(value: Double): String {
    if (value.isNaN()) return "-"
    val rounded = Math.round(value * 1000.0) / 1000.0
    return if (rounded % 1.0 == 0.0) {
        rounded.toLong().toString()
    } else {
        rounded.toString().trimEnd('0').trimEnd('.')
    }
}

/**
 * Convert a local wall-clock timestamp to the UTC-midnight value that
 * Material 3's [androidx.compose.material3.DatePicker] expects for its
 * selection, so the picker opens on the correct calendar day.
 */
fun toDatePickerUtcMillis(localMillis: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = localMillis }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(
            local.get(Calendar.YEAR),
            local.get(Calendar.MONTH),
            local.get(Calendar.DAY_OF_MONTH),
        )
    }.timeInMillis
}

/**
 * Apply a date picked in the DatePicker (UTC-midnight millis) onto an existing
 * local timestamp, keeping that timestamp's time-of-day intact.
 */
fun applyDatePickerMillis(pickedUtcMillis: Long, timeSourceLocalMillis: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = pickedUtcMillis }
    return Calendar.getInstance().apply {
        timeInMillis = timeSourceLocalMillis
        set(Calendar.YEAR, utc.get(Calendar.YEAR))
        set(Calendar.MONTH, utc.get(Calendar.MONTH))
        set(Calendar.DAY_OF_MONTH, utc.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

/** Human-friendly elapsed time between two instants, e.g. "5 days". */
fun formatSpan(fromMillis: Long, toMillis: Long): String {
    val days = abs(toMillis - fromMillis) / (1000L * 60 * 60 * 24)
    return when {
        days <= 0 -> "same day"
        days == 1L -> "1 day"
        days < 45 -> "$days days"
        days < 365 -> "${(days + 15) / 30} months"
        else -> String.format(Locale.getDefault(), "%.1f years", days / 365.0)
    }
}
