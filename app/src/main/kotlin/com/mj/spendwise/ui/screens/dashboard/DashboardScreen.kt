package com.mj.spendwise.ui.screens.dashboard

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mj.spendwise.ui.components.PlaceholderScreen
import com.mj.spendwise.ui.components.SyncChip
import com.mj.spendwise.viewmodel.ExpenseViewModel

/** Sync chip + network type are real; totals, projection and charts arrive in Phase 5. */
@Composable
fun DashboardScreen(vm: ExpenseViewModel, onScanReceipt: () -> Unit, modifier: Modifier = Modifier) {
    val status by vm.syncStatus.collectAsStateWithLifecycle()
    val pending by vm.pendingCount.collectAsStateWithLifecycle()
    val network by vm.networkType.collectAsStateWithLifecycle()

    PlaceholderScreen("Dashboard", "Totals, projection and charts arrive in Phase 5.", modifier) {
        SyncChip(status, pending)
        Text("Network: $network")
        // Navigates to add_expense?scan=true, i.e. navigation with an argument.
        Button(onClick = onScanReceipt) { Text("Scan receipt") }
    }
}
