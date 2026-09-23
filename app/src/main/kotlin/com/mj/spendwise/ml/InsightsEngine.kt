package com.mj.spendwise.ml

import com.mj.spendwise.backend.Expense
import com.mj.spendwise.util.formatInr
import com.mj.spendwise.util.formatInrWhole
import com.mj.spendwise.util.localDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToInt

data class Insight(val title: String, val text: String, val severity: String = "info")

data class DayTotal(val date: LocalDate, val day: DayOfWeek, val total: Double)

/**
 * All dashboard/analytics numbers come from here. Plain Kotlin (no Android, no Firebase calls), so it is
 * unit-testable: give it a list of expenses, "today" and the monthly budget.
 *
 * Statistics used (viva notes):
 *  - projection = spent so far / days elapsed * days in month (a straight-line pace)
 *  - month-over-month compares the same number of days in both months, so a half-finished month
 *    isn't compared with a full one.
 */
class InsightsEngine(
    expenses: List<Expense>,
    private val today: LocalDate = LocalDate.now(),
    val monthlyBudget: Double = 30000.0
) {
    private data class Row(val date: LocalDate, val amount: Double, val category: String)

    private val rows = expenses.map { Row(it.localDate(), it.amount, it.category.orEmpty()) }
    private val thisMonth = YearMonth.from(today)

    val daysElapsed: Int get() = today.dayOfMonth
    val daysInMonth: Int get() = thisMonth.lengthOfMonth()

    fun monthTotal(month: YearMonth = thisMonth): Double =
        rows.filter { YearMonth.from(it.date) == month }.sumOf { it.amount }

    fun previousMonthTotal(): Double = monthTotal(thisMonth.minusMonths(1))

    /** Last month's spending in its first [daysElapsed] days (the fair comparison for a partial month). */
    fun previousMonthSamePeriod(): Double {
        val prev = thisMonth.minusMonths(1)
        return rows.filter { YearMonth.from(it.date) == prev && it.date.dayOfMonth <= daysElapsed }.sumOf { it.amount }
    }

    /** spentSoFar / daysElapsed * daysInMonth. Guards daysElapsed == 0 by returning what was spent. */
    fun projectedMonthEnd(): Double {
        val spent = monthTotal()
        return if (daysElapsed <= 0) spent else spent / daysElapsed * daysInMonth
    }

    /** % change vs the same period last month; null when last month had nothing (avoids divide-by-zero). */
    fun monthOverMonthPercent(): Double? {
        val previous = previousMonthSamePeriod()
        if (previous <= 0.0) return null
        return (monthTotal() - previous) / previous * 100.0
    }

    fun budgetUsedFraction(): Double = if (monthlyBudget <= 0) 0.0 else monthTotal() / monthlyBudget

    /** Category -> total for the month, biggest first. */
    fun categoryTotals(month: YearMonth = thisMonth): Map<String, Double> =
        rows.filter { YearMonth.from(it.date) == month }
            .groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }
            .associate { it.key to it.value }

    /** The last 7 days (oldest first, ending today), one total per weekday. */
    fun weeklyByDayOfWeek(): List<DayTotal> = (6 downTo 0).map { back ->
        val date = today.minusDays(back.toLong())
        DayTotal(date, date.dayOfWeek, rows.filter { it.date == date }.sumOf { it.amount })
    }

    /** Totals for the last [months] months including this one (oldest first). */
    fun monthlyTrend(months: Int = 3): List<Pair<YearMonth, Double>> =
        (months - 1 downTo 0).map { back -> thisMonth.minusMonths(back.toLong()).let { it to monthTotal(it) } }

    /** Average monthly total of a category over the previous 3 full months. */
    fun threeMonthAverage(category: String): Double =
        (1..3).map { back -> categoryTotals(thisMonth.minusMonths(back.toLong()))[category] ?: 0.0 }.average()

    /** Human-readable insight cards for the dashboard, most important first. */
    fun generateInsights(): List<Insight> {
        val out = mutableListOf<Insight>()
        val current = categoryTotals()

        // 1) The category that is furthest above its own 3-month average.
        current.mapNotNull { (category, total) ->
            val avg = threeMonthAverage(category)
            if (avg > 0 && total > avg * 1.2) Triple(category, total, (total / avg - 1) * 100) else null
        }.maxByOrNull { it.third }?.let { (category, _, pct) ->
            out += Insight(
                "$category is up", "$category is ${pct.roundToInt()}% higher than your 3-month average.",
                if (pct > 50) "warning" else "info"
            )
        }

        // 2) Budget pace.
        if (monthlyBudget > 0) {
            val projected = projectedMonthEnd()
            val diff = projected - monthlyBudget
            out += if (diff > 0) {
                Insight("Over budget pace", "At this pace you'll spend ${formatInrWhole(projected)}, ${formatInrWhole(diff)} over budget.", "warning")
            } else {
                Insight("On track", "At this pace you'll spend ${formatInrWhole(projected)}, ${formatInrWhole(abs(diff))} under budget.")
            }
        }

        // 3) Biggest category.
        val total = monthTotal()
        current.entries.firstOrNull()?.let { (category, amount) ->
            if (total > 0) out += Insight("Top category", "$category is your biggest spend: ${formatInr(amount)} (${(amount / total * 100).roundToInt()}% of this month).")
        }
        return out
    }
}
