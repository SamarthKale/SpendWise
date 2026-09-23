package com.mj.spendwise.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AnomalyDetectorTest {
    private val today = LocalDate.of(2026, 9, 20)

    private fun steadyHistory(category: String = "Food") = listOf(
        expense(LocalDate.of(2026, 8, 10), 1000.0, category),
        expense(LocalDate.of(2026, 7, 10), 1000.0, category),
        expense(LocalDate.of(2026, 6, 10), 1000.0, category)
    )

    @Test
    fun categoryFarAboveSteadyHistory_isFlagged() {
        val list = steadyHistory() + expense(LocalDate.of(2026, 9, 5), 2500.0)
        val anomalies = AnomalyDetector.detect(list, today).filter { it.kind == "category" }
        assertEquals(1, anomalies.size)
        assertEquals("Food", anomalies[0].category)
        assertEquals(1000.0, anomalies[0].expectedValue, 0.001)
        assertEquals("critical", anomalies[0].severity) // 2.5x the usual
    }

    @Test
    fun normalSpending_isNotFlagged() {
        val list = steadyHistory() + expense(LocalDate.of(2026, 9, 5), 1100.0)
        assertTrue(AnomalyDetector.detect(list, today).none { it.kind == "category" })
    }

    @Test
    fun noHistory_isNotFlagged() {
        // A brand-new category has nothing to compare against.
        assertTrue(AnomalyDetector.detect(listOf(expense(today, 99999.0, "Health")), today).isEmpty())
    }

    @Test
    fun oneHugeTransaction_isFlaggedAsTransaction() {
        val history = listOf(100.0, 120.0, 110.0, 90.0, 105.0).mapIndexed { i, a ->
            expense(LocalDate.of(2026, 8, 1 + i), a, "Fuel")
        }
        val list = history + expense(LocalDate.of(2026, 9, 2), 900.0, "Fuel", id = "big")
        val tx = AnomalyDetector.detect(list, today).filter { it.kind == "transaction" }
        assertEquals(1, tx.size)
        assertEquals("big", tx[0].expenseId)
    }

    @Test
    fun stdDev_ofConstantValues_isZero() {
        assertEquals(0.0, AnomalyDetector.stdDev(listOf(5.0, 5.0, 5.0)), 0.0)
        assertEquals(0.0, AnomalyDetector.stdDev(emptyList()), 0.0)
    }
}
