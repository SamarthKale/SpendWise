package com.spendwise.ui.screens.expenses

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.spendwise.ui.components.PlaceholderScreen
import com.spendwise.ui.screens.scan.ReceiptScannerSheet

/**
 * @param scan true when opened via "Scan receipt": the scanner sheet opens immediately.
 * @param editId non-null when editing an existing expense.
 */
@Composable
fun AddEditExpenseScreen(scan: Boolean, editId: String?, modifier: Modifier = Modifier) {
    var showSheet by rememberSaveable { mutableStateOf(scan) }
    val mode = if (editId == null) "Add expense" else "Edit expense ($editId)"

    PlaceholderScreen(mode, "Form fields arrive in Phase 2. scan=$scan", modifier) {
        OutlinedButton(onClick = { showSheet = true }) { Text("Scan receipt") }
    }
    if (showSheet) ReceiptScannerSheet(onDismiss = { showSheet = false })
}
