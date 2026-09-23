package com.mj.spendwise.ml

import com.mj.spendwise.backend.Expense
import com.mj.spendwise.util.toTimestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

internal fun expense(date: LocalDate, amount: Double, category: String = "Food", id: String = "x"): Expense =
    Expense().apply {
        this.id = id
        this.amount = amount
        this.category = category
        this.merchant = "Test"
        timestamp = date.atTime(12, 0).toTimestamp()
    }

class InsightsEngineTest {
    private val today = LocalDate.of(2026, 9, 10) // 10 days elapsed of a 30-day month

    @Test
    fun projection_isLinearPace() {
        // 1000 spent in 10 days -> 100/day -> 3000 over the 30-day month
        val engine = InsightsEngine(listOf(expense(today.minusDays(3), 1000.0)), today, 5000.0)
        assertEquals(3000.0, engine.projectedMonthEnd(), 0.001)
    }

    @Test
    fun projection_withNoExpenses_isZero() {
        assertEquals(0.0, InsightsEngine(emptyList(), today).projectedMonthEnd(), 0.001)
    }

    @Test
    fun projection_guardsZeroDaysElapsed() {
        // A "today" before day 1 can't happen, but the guard must not divide by zero.
        val engine = InsightsEngine(listOf(expense(today, 500.0)), LocalDate.of(2026, 9, 1), 1000.0)
        assertEquals(500.0 * 30, engine.projectedMonthEnd(), 0.001) // day 1: 500 / 1 * 30
    }

    @Test
    fun monthOverMonth_nullWhenLastMonthEmpty() {
        val engine = InsightsEngine(listOf(expense(today, 100.0)), today)
        assertNull(engine.monthOverMonthPercent())
    }

    @Test
    fun monthOverMonth_comparesSamePeriod() {
        val list = listOf(
            expense(LocalDate.of(2026, 8, 5), 100.0),   // Aug 1-10: counts
            expense(LocalDate.of(2026, 8, 25), 9999.0), // Aug 11-31: must be ignored
            expense(LocalDate.of(2026, 9, 5), 150.0)
        )
        assertEquals(50.0, InsightsEngine(list, today).monthOverMonthPercent()!!, 0.001)
    }

    @Test
    fun categoryTotals_sortedBiggestFirst() {
        val list = listOf(
            expense(today, 100.0, "Food"), expense(today, 400.0, "Fuel"), expense(today, 50.0, "Food")
        )
        val totals = InsightsEngine(list, today).categoryTotals()
        assertEquals(listOf("Fuel", "Food"), totals.keys.toList())
        assertEquals(150.0, totals["Food"]!!, 0.001)
    }

    @Test
    fun weekly_hasSevenDaysEndingToday() {
        val list = listOf(expense(today, 200.0), expense(today.minusDays(6), 30.0), expense(today.minusDays(7), 999.0))
        val week = InsightsEngine(list, today).weeklyByDayOfWeek()
        assertEquals(7, week.size)
        assertEquals(today, week.last().date)
        assertEquals(200.0, week.last().total, 0.001)
        assertEquals(30.0, week.first().total, 0.001) // 8 days ago is excluded
    }

    @Test
    fun trend_returnsThreeMonthsOldestFirst() {
        val list = listOf(expense(LocalDate.of(2026, 7, 3), 10.0), expense(LocalDate.of(2026, 9, 3), 30.0))
        val trend = InsightsEngine(list, today).monthlyTrend(3)
        assertEquals(YearMonth.of(2026, 7), trend.first().first)
        assertEquals(listOf(10.0, 0.0, 30.0), trend.map { it.second })
    }

    @Test
    fun insights_flagOverBudgetPace() {
        val engine = InsightsEngine(listOf(expense(today, 4000.0)), today, 5000.0) // pace 12000 > 5000
        val insights = engine.generateInsights()
        assertNotNull(insights.firstOrNull { it.title == "Over budget pace" })
        assertTrue(insights.any { it.severity == "warning" })
    }
}
