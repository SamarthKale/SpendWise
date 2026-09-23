package com.spendwise.ui.screens.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.ui.components.EmptyState
import com.spendwise.ui.components.LoadingState
import com.spendwise.util.formatInr
import com.spendwise.util.prettyDateTime
import com.spendwise.viewmodel.ExpenseViewModel

@Composable
fun ExpenseDetailScreen(
    vm: ExpenseViewModel,
    expenseId: String,
    onEdit: () -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val expenses by vm.expenses.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val list = expenses
    val expense = list?.firstOrNull { it.id == expenseId }
    when {
        list == null -> LoadingState(modifier)
        expense == null -> EmptyState("Expense not found", "It may have been deleted.", modifier)
        else -> Column(
            modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(expense.merchant, style = MaterialTheme.typography.headlineSmall)
                    Text(formatInr(expense.amount), style = MaterialTheme.typography.displaySmall)
                }
            }
            DetailRow("Category", expense.category)
            DetailRow("Date", expense.prettyDateTime())
            if (!expense.notes.isNullOrBlank()) DetailRow("Notes", expense.notes)
            if (!expense.locationName.isNullOrBlank()) DetailRow("Location", expense.locationName)
            DetailRow("Source", if (expense.source == "receipt_scan") "Receipt scan" else "Manual entry")
            if (expense.pending) DetailRow("Sync", "Pending - will upload when online")

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onEdit) { Text("Edit") }
                Button(onClick = { confirmDelete = true }) { Text("Delete") }
            }
        }
    }

    // Delete-confirm AlertDialog (pattern 6 in 8.1)
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete expense?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteExpense(expenseId)
                    onDeleted()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
