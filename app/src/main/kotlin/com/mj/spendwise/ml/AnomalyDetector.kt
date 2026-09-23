package com.mj.spendwise.ml

import com.mj.spendwise.backend.Expense
import com.mj.spendwise.util.localDate
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.sqrt

/**
 * @param kind "category" (a whole category is unusually high this month) or "transaction" (one big expense)
 * @param severity "warning" or "critical"
 */
data class Anomaly(
    val category: String,
    val currentValue: Double,
    val expectedValue: Double,
    val severity: String,
    val kind: String = "category",
    val merchant: String? = null,
    val expenseId: String? = null
)

/**
 * Statistical anomaly detection (no trained model). For each category:
 *  - category-level: this month's spend so far is flagged if it is above mean + 2 sigma of the previous
 *    3 monthly totals (or above 1.5 x mean when the history is very steady, sigma tiny)
 *  - transaction-level: an expense this month is flagged if it is above mean + 3 sigma of that
 *    category's earlier transactions.
 * The month-to-date value is compared with full past months, which makes the check conservative early in the month.
 */
object AnomalyDetector {

    fun detect(expenses: List<Expense>, today: LocalDate = LocalDate.now()): List<Anomaly> {
        val thisMonth = YearMonth.from(today)
        val byCategory = expenses.groupBy { it.category.orEmpty() }.filterKeys { it.isNotBlank() }
        val out = mutableListOf<Anomaly>()

        for ((category, list) in byCategory) {
            // ---- category vs its previous 3 monthly totals ----
            val history = (1..3).map { back ->
                val m = thisMonth.minusMonths(back.toLong())
                list.filter { YearMonth.from(it.localDate()) == m }.sumOf { it.amount }
            }
            val mean = history.average()
            val sigma = stdDev(history)
            val current = list.filter { YearMonth.from(it.localDate()) == thisMonth }.sumOf { it.amount }
            if (mean > 0 && current > 0) {
                val sigmaTiny = sigma < 0.1 * mean
                val flagged = if (sigmaTiny) current > 1.5 * mean else current > mean + 2 * sigma
                if (flagged) {
                    out += Anomaly(category, current, mean, if (current / mean >= 2.5) "critical" else "warning")
                }
            }

            // ---- single big transactions this month vs earlier transactions ----
            val past = list.filter { YearMonth.from(it.localDate()) < thisMonth }.map { it.amount }
            if (past.size >= 3) {
                val pMean = past.average()
                val pSigma = maxOf(stdDev(past), 0.05 * pMean) // floor so identical history doesn't flag everything
                // Also require 2x the usual: with very steady history sigma is tiny and 3 sigma alone flags modest bills.
                list.filter { YearMonth.from(it.localDate()) == thisMonth && it.amount > pMean + 3 * pSigma && it.amount >= 2 * pMean }
                    .forEach { e ->
                        out += Anomaly(category, e.amount, pMean, "warning", "transaction", e.merchant, e.id)
                    }
            }
        }
        return out.sortedByDescending { it.currentValue / it.expectedValue }
    }

    /** Population standard deviation. */
    fun stdDev(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
    }
}
