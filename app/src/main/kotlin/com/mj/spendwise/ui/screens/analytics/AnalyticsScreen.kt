package com.mj.spendwise.ui.screens.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mj.spendwise.ui.components.EmptyState
import com.mj.spendwise.ui.components.LoadingState
import com.mj.spendwise.viewmodel.ExpenseViewModel

/** Three tabs (pattern 9 in 8.1). All values come from InsightsEngine / AnomalyDetector. */
@Composable
fun AnalyticsScreen(vm: ExpenseViewModel, modifier: Modifier = Modifier) {
    val engine by vm.engine.collectAsStateWithLifecycle()
    val anomalies by vm.anomalies.collectAsStateWithLifecycle()
    val budget by vm.budget.collectAsStateWithLifecycle()
    val startup by vm.startup.collectAsStateWithLifecycle()

    val tabs = listOf("This Month", "Categories", "Trends")
    var selected by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selected) {
            tabs.forEachIndexed { i, label ->
                Tab(selected = selected == i, onClick = { selected = i }, text = { Text(label) })
            }
        }
        val e = engine
        if (e == null) {
            val failed = startup as? ExpenseViewModel.Startup.Failed
            if (failed != null) EmptyState("Cloud unavailable", failed.message) else LoadingState()
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (selected) {
                    0 -> thisMonthTab(e)
                    1 -> categoriesTab(e, budget.categoryBudgets.orEmpty())
                    else -> trendsTab(e, anomalies)
                }
            }
        }
    }
}
