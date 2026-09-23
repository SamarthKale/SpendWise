package com.mj.spendwise.notifications

import com.mj.spendwise.ml.expense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AlertRulesTest {
    private val today = LocalDate.of(2026, 9, 20)

    private fun types(spent: Double, budget: Double) =
        AlertRules.evaluate(listOf(expense(today, spent, "Health")), budget, today).map { it.type }

    @Test
    fun under80Percent_noBudgetAlert() {
        val t = types(3000.0, 10000.0)
        assertTrue("budget_80" !in t && "budget_100" !in t)
    }

    @Test
    fun at80Percent_warning() {
        val alert = AlertRules.evaluate(listOf(expense(today, 8500.0, "Health")), 10000.0, today).first { it.type == "budget_80" }
        assertEquals("warning", alert.severity)
        assertEquals("budget80_2026-09", alert.dedupeKey)
    }

    @Test
    fun atOrOver100Percent_critical_andNoDuplicate80Alert() {
        val alerts = AlertRules.evaluate(listOf(expense(today, 12000.0, "Health")), 10000.0, today)
        val a = alerts.first { it.type == "budget_100" }
        assertEquals("critical", a.severity)
        assertTrue(alerts.none { it.type == "budget_80" })
    }

    @Test
    fun projectionOvershoot_isInfo() {
        // 5000 in 20 days -> pace 7500 for a 30-day month, over a 6000 budget (but only 83% used).
        val p = AlertRules.evaluate(listOf(expense(today, 5000.0, "Health")), 6000.0, today).first { it.type == "projection" }
        assertEquals("info", p.severity)
    }

    @Test
    fun sameInputs_giveSameDedupeKeys() {
        val list = listOf(expense(today, 9000.0, "Health"))
        val first = AlertRules.evaluate(list, 10000.0, today).map { it.dedupeKey }
        val second = AlertRules.evaluate(list, 10000.0, today).map { it.dedupeKey }
        assertEquals(first, second) // stable keys are what stop the same alert being created twice
    }

    @Test
    fun bigTicketTransaction_keyedByExpenseId() {
        val history = listOf(100.0, 120.0, 110.0, 90.0, 105.0).mapIndexed { i, a ->
            expense(LocalDate.of(2026, 8, 1 + i), a, "Fuel", id = "h$i")
        }
        val big = expense(today, 900.0, "Fuel", id = "big")
        val alerts = AlertRules.evaluate(history + big, 100000.0, today, justSaved = big)
        assertNotNull(alerts.firstOrNull { it.dedupeKey == "bigticket_big" })
    }

    @Test
    fun bigTicket_notRaisedForOldExpenses() {
        val history = listOf(100.0, 120.0, 110.0, 90.0, 105.0).mapIndexed { i, a ->
            expense(LocalDate.of(2026, 8, 1 + i), a, "Fuel", id = "h$i")
        }
        val old = expense(today, 900.0, "Fuel", id = "old")
        val alerts = AlertRules.evaluate(history + old, 100000.0, today, justSaved = history.first())
        assertTrue(alerts.none { it.type == "big_ticket" }) // only the just-saved expense may raise one
    }

    @Test
    fun noBudget_noBudgetAlerts() {
        assertNull(AlertRules.evaluate(listOf(expense(today, 500.0)), 0.0, today).firstOrNull { it.type.startsWith("budget") })
    }
}
