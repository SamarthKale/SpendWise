package com.mj.spendwise.ui.screens.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mj.spendwise.categoryColor
import com.mj.spendwise.ml.Anomaly
import com.mj.spendwise.ml.InsightsEngine
import com.mj.spendwise.ui.components.BarChart
import com.mj.spendwise.ui.components.InsightCard
import com.mj.spendwise.util.formatInr
import com.mj.spendwise.util.formatInrWhole
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// ---------- Tab 1: this month ----------
fun LazyListScope.thisMonthTab(e: InsightsEngine) {
    item {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Projected month-end", style = MaterialTheme.typography.labelLarge)
                Text(formatInrWhole(e.projectedMonthEnd()), style = MaterialTheme.typography.headlineLarge)
                Text(
                    "Spent ${formatInr(e.monthTotal())} in ${e.daysElapsed} of ${e.daysInMonth} days " +
                        "(${formatInrWhole(e.monthTotal() / e.daysElapsed.coerceAtLeast(1))} per day).",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
    item {
        val mom = e.monthOverMonthPercent()
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Month over month", style = MaterialTheme.typography.labelLarge)
                if (mom == null) {
                    Text("No spending last month to compare with.")
                } else {
                    val up = mom >= 0
                    Text(
                        "${if (up) "▲" else "▼"} ${abs(mom).roundToInt()}%",
                        style = MaterialTheme.typography.headlineLarge,
                        color = if (up) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "This month so far ${formatInr(e.monthTotal())} vs ${formatInr(e.previousMonthSamePeriod())} " +
                            "in the same days last month.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
    val insights = e.generateInsights()
    items(insights.size) { i -> InsightCard(insights[i]) }
}

// ---------- Tab 2: categories ----------
fun LazyListScope.categoriesTab(e: InsightsEngine, categoryBudgets: Map<String, Double>) {
    val totals = e.categoryTotals()
    val month = e.monthTotal()
    if (totals.isEmpty()) {
        item { Text("Nothing spent yet this month.") }
        return
    }
    items(totals.entries.toList()) { (category, amount) ->
        val share = if (month > 0) amount / month else 0.0
        val limit = categoryBudgets[category]
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(categoryColor(category)))
                Text(category, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Text("${formatInr(amount)} · ${(share * 100).roundToInt()}%")
            }
            LinearProgressIndicator(
                progress = { share.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = categoryColor(category)
            )
            if (limit != null && limit > 0) {
                Text(
                    "Budget ${formatInr(limit)}: " + if (amount > limit) "over by ${formatInr(amount - limit)}" else "${formatInr(limit - amount)} left",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (amount > limit) MaterialTheme.colorScheme.error else Color.Unspecified
                )
            }
        }
    }
}

// ---------- Tab 3: 3-month trend + anomalies ----------
fun LazyListScope.trendsTab(e: InsightsEngine, anomalies: List<Anomaly>) {
    item {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Last 3 months", style = MaterialTheme.typography.titleMedium)
                val trend = e.monthlyTrend(3)
                BarChart(
                    bars = trend.map { (m, total) -> m.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) to total },
                    highlightIndex = trend.lastIndex,
                    chartHeight = 160
                )
                Text("The current month is still in progress.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    item { Text("Unusual spending", style = MaterialTheme.typography.titleMedium) }
    if (anomalies.isEmpty()) {
        item { Text("No anomalies detected. Your spending looks normal.") }
    }
    items(anomalies.size) { i ->
        val a = anomalies[i]
        val text = if (a.kind == "transaction") {
            "${a.merchant ?: "An expense"} (${a.category}) of ${formatInr(a.currentValue)} is far above your usual ${formatInrWhole(a.expectedValue)} per ${a.category} purchase."
        } else {
            "${a.category} is ${formatInr(a.currentValue)} so far vs a usual ${formatInrWhole(a.expectedValue)} per month " +
                "(${"%.1f".format(a.currentValue / a.expectedValue)}×)."
        }
        InsightCard(com.mj.spendwise.ml.Insight(if (a.kind == "transaction") "Big transaction" else "${a.category} spike", text, a.severity))
    }
    item {
        Text(
            "On-device ML: OCR (ML Kit), keyword classifier, statistical anomaly detection.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
