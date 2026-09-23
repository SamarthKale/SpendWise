package com.spendwise.ui.screens.dashboard

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.spendwise.ui.components.PlaceholderScreen

@Composable
fun DashboardScreen(onScanReceipt: () -> Unit, modifier: Modifier = Modifier) {
    PlaceholderScreen("Dashboard", "Totals, projection and charts arrive in Phase 5.", modifier) {
        // Navigates to add_expense?scan=true, i.e. navigation with an argument.
        Button(onClick = onScanReceipt) { Text("Scan receipt") }
    }
}
