package com.spendwise.util

import com.google.firebase.Timestamp
import com.spendwise.backend.Expense
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

private val EN_IN: Locale = Locale.Builder().setLanguage("en").setRegion("IN").build()

/** ₹1,234 or ₹1,234.50 (en-IN grouping). */
fun formatInr(amount: Double): String {
    val f = NumberFormat.getCurrencyInstance(EN_IN)
    f.minimumFractionDigits = 0
    f.maximumFractionDigits = 2
    return f.format(amount)
}

// Timestamp <-> LocalDate always use the device time zone (CLAUDE.md 14).
fun Timestamp.toLocalDateTime(): LocalDateTime =
    toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()

fun LocalDateTime.toTimestamp(): Timestamp =
    Timestamp(Date.from(atZone(ZoneId.systemDefault()).toInstant()))

fun Expense.localDate(): LocalDate = timestamp.toLocalDateTime().toLocalDate()

private val DATE_FMT = DateTimeFormatter.ofPattern("d MMM yyyy", EN_IN)
private val DATE_TIME_FMT = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", EN_IN)

fun LocalDate.pretty(): String = format(DATE_FMT)
fun Expense.prettyDateTime(): String = timestamp.toLocalDateTime().format(DATE_TIME_FMT)

/** "Today" / "Yesterday" / "24 Sep 2026" for list headers. */
fun LocalDate.relativeLabel(today: LocalDate = LocalDate.now()): String = when (this) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> pretty()
}
