package com.diprish.utilitymeter.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
