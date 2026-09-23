package com.mj.spendwise.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mj.spendwise.util.formatInr
import com.mj.spendwise.viewmodel.ExpenseViewModel

@Composable
fun SettingsScreen(vm: ExpenseViewModel, modifier: Modifier = Modifier) {
    val uid by vm.uid.collectAsStateWithLifecycle()
    val wifiOnly by vm.wifiOnly.collectAsStateWithLifecycle()
    val budget by vm.budget.collectAsStateWithLifecycle()
    var showBudget by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Budget", style = MaterialTheme.typography.titleMedium)
        Text("Monthly budget: ${formatInr(budget.monthlyBudget)}")
        Button(onClick = { showBudget = true }) { Text("Edit budgets") }

        Text("Sync", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Sync on Wi-Fi only", modifier = Modifier.weight(1f))
            Switch(checked = wifiOnly, onCheckedChange = { vm.setWifiOnly(it) })
        }
        OutlinedButton(onClick = { vm.reseedDemoData() }) { Text("Reseed demo data") }

        Text("About", style = MaterialTheme.typography.titleMedium)
        Text("Firebase uid: ${uid ?: "not signed in"}", style = MaterialTheme.typography.bodySmall)
    }

    if (showBudget) {
        BudgetDialog(
            monthly = budget.monthlyBudget,
            categoryBudgets = budget.categoryBudgets.orEmpty(),
            onSave = { monthly, cats ->
                vm.saveBudget(monthly, cats)
                showBudget = false
            },
            onDismiss = { showBudget = false }
        )
    }
}
