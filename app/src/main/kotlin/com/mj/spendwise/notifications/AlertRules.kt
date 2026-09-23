package com.mj.spendwise.notifications

import com.mj.spendwise.backend.AlertItem
import com.mj.spendwise.backend.Expense
import com.mj.spendwise.ml.AnomalyDetector
import com.mj.spendwise.ml.InsightsEngine
import com.mj.spendwise.util.formatInr
import com.mj.spendwise.util.formatInrWhole
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth

/**
 * Rule-based "AI" alerts (CLAUDE.md 9.7): the notifications are real, the triggers are simple rules.
 * Every alert has a dedupeKey (rule + month, or the expense id) so the same alert is never created twice.
 * Pure Kotlin: takes data in, returns AlertItems, so it is unit-testable.
 */
object AlertRules {

    /**
     * @param justSaved the expense the user just added/edited. A big-ticket alert is only raised for that
     * expense, so old expenses don't all fire notifications at once.
     */
    fun evaluate(
        expenses: List<Expense>,
        monthlyBudget: Double,
        today: LocalDate = LocalDate.now(),
        justSaved: Expense? = null
    ): List<AlertItem> {
        val engine = InsightsEngine(expenses, today, monthlyBudget)
        val month = YearMonth.from(today).toString() // e.g. "2026-09"
        val out = mutableListOf<AlertItem>()

        if (monthlyBudget > 0) {
            val spent = engine.monthTotal()
            val used = engine.budgetUsedFraction()
            val payload = { rule: String -> JSONObject().put("rule", rule).put("spent", spent).put("budget", monthlyBudget).put("usedPercent", (used * 100).toInt()) }

            if (used >= 1.0) {
                out += alert("budget_100", "budget100_$month", "Budget exceeded", "critical",
                    "You've spent ${formatInr(spent)}, over your ${formatInr(monthlyBudget)} monthly budget.", payload("budget_100"))
            } else if (used >= 0.8) {
                out += alert("budget_80", "budget80_$month", "80% of budget used", "warning",
                    "You've used ${(used * 100).toInt()}% of your budget (${formatInr(spent)} of ${formatInr(monthlyBudget)}).", payload("budget_80"))
            }

            val projected = engine.projectedMonthEnd()
            if (projected > monthlyBudget) {
                out += alert("projection", "projection_$month", "Projected to overshoot", "info",
                    "At this pace you'll spend ${formatInrWhole(projected)}, ${formatInrWhole(projected - monthlyBudget)} over budget.",
                    payload("projection").put("projected", projected))
            }
        }

        for (a in AnomalyDetector.detect(expenses, today)) {
            val payload = JSONObject().put("category", a.category).put("current", a.currentValue).put("expected", a.expectedValue).put("kind", a.kind)
            if (a.kind == "transaction") {
                if (justSaved == null || a.expenseId != justSaved.id) continue
                out += alert("big_ticket", "bigticket_${a.expenseId}", "Big-ticket expense", "info",
                    "${a.merchant ?: "An expense"} (${a.category}) of ${formatInr(a.currentValue)} is far above your usual ${formatInrWhole(a.expectedValue)}.",
                    payload.put("rule", "big_ticket"))
            } else {
                out += alert("anomaly", "anomaly_${a.category}_$month", "Unusual ${a.category} spending", a.severity,
                    "${a.category} is ${formatInr(a.currentValue)} this month vs a usual ${formatInrWhole(a.expectedValue)}.",
                    payload.put("rule", "anomaly"))
            }
        }
        return out
    }

    private fun alert(type: String, key: String, title: String, severity: String, body: String, payload: JSONObject) =
        AlertItem().apply {
            this.type = type
            this.dedupeKey = key
            this.title = title
            this.severity = severity
            this.body = "$body (AI-generated, demo rules)"
            this.payloadJson = payload.toString(2)
        }
}
