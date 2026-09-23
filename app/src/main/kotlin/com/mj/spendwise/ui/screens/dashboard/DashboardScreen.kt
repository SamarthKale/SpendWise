package com.mj.spendwise.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mj.spendwise.categoryColor
import com.mj.spendwise.ml.InsightsEngine
import com.mj.spendwise.ui.components.BarChart
import com.mj.spendwise.ui.components.EmptyState
import com.mj.spendwise.ui.components.ExpenseCard
import com.mj.spendwise.ui.components.InsightCard
import com.mj.spendwise.ui.components.LoadingState
import com.mj.spendwise.ui.components.SyncChip
import com.mj.spendwise.util.formatInr
import com.mj.spendwise.util.formatInrWhole
import com.mj.spendwise.viewmodel.ExpenseViewModel
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Every number here is computed by InsightsEngine from the live Firestore list: nothing is hardcoded. */
@Composable
fun DashboardScreen(
    vm: ExpenseViewModel,
    onScanReceipt: () -> Unit,
    onOpenExpense: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val engine by vm.engine.collectAsStateWithLifecycle()
    val expenses by vm.expenses.collectAsStateWithLifecycle()
    val startup by vm.startup.collectAsStateWithLifecycle()
    val status by vm.syncStatus.collectAsStateWithLifecycle()
    val pending by vm.pendingCount.collectAsStateWithLifecycle()
    val network by vm.networkType.collectAsStateWithLifecycle()

    val e = engine
    if (e == null) {
        val failed = startup as? ExpenseViewModel.Startup.Failed
        if (failed != null) EmptyState("Cloud unavailable", failed.message, modifier) else LoadingState(modifier)
        return
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(greeting(), style = MaterialTheme.typography.headlineSmall)
                    Text("Network: $network", style = MaterialTheme.typography.bodySmall)
                }
                SyncChip(status, pending)
            }
        }
        item { OutlinedButton(onClick = onScanReceipt, Modifier.fillMaxWidth()) { Text("Scan receipt") } }
        item { MonthCard(e) }
        item { ProjectionCard(e) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Last 7 days", style = MaterialTheme.typography.titleMedium)
                    val week = e.weeklyByDayOfWeek()
                    BarChart(
                        bars = week.map { it.day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) to it.total },
                        highlightIndex = week.lastIndex
                    )
                }
            }
        }
        item { TopCategories(e) }
        val insights = e.generateInsights().take(2)
        if (insights.isNotEmpty()) {
            item {
                Text("AI insights (rule-based demo)", style = MaterialTheme.typography.titleMedium)
            }
            items(insights) { InsightCard(it) }
        }
        item { Text("Recent expenses", style = MaterialTheme.typography.titleMedium) }
        val recent = expenses.orEmpty().take(5)
        if (recent.isEmpty()) item { Text("No expenses yet. Tap + to add one.") }
        items(recent, key = { it.id }) { ExpenseCard(it, onClick = { onOpenExpense(it.id) }) }
        item { Box(Modifier.size(72.dp)) } // room for the FAB
    }
}

private fun greeting(): String {
    val hour = LocalTime.now().hour
    return when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
}

@Composable
private fun MonthCard(e: InsightsEngine) {
    val spent = e.monthTotal()
    val used = e.budgetUsedFraction()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("This month", style = MaterialTheme.typography.labelLarge)
            Text(formatInr(spent), style = MaterialTheme.typography.displaySmall)
            Text("of ${formatInr(e.monthlyBudget)} budget", style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(
                progress = { used.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = if (used >= 1.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            val remaining = e.monthlyBudget - spent
            Text(
                if (remaining >= 0) "${(used * 100).roundToInt()}% used · ${formatInr(remaining)} left"
                else "Over budget by ${formatInr(-remaining)}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ProjectionCard(e: InsightsEngine) {
    val projected = e.projectedMonthEnd()
    val diff = projected - e.monthlyBudget
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Projected month-end", style = MaterialTheme.typography.labelLarge)
            Text(formatInrWhole(projected), style = MaterialTheme.typography.headlineMedium)
            Text(
                if (diff > 0) "${formatInrWhole(diff)} over budget at this pace" else "${formatInrWhole(abs(diff))} under budget at this pace",
                color = if (diff > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            Text(
                "Based on ${e.daysElapsed} of ${e.daysInMonth} days elapsed",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun TopCategories(e: InsightsEngine) {
    val totals = e.categoryTotals().entries.take(3)
    val month = e.monthTotal()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Top categories", style = MaterialTheme.typography.titleMedium)
            if (totals.isEmpty()) Text("Nothing spent yet this month.")
            totals.forEach { (category, amount) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(categoryColor(category)))
                    Text(category, Modifier.weight(1f))
                    Text("${formatInr(amount)}  (${(amount / month * 100).roundToInt()}%)")
                }
            }
        }
    }
}
