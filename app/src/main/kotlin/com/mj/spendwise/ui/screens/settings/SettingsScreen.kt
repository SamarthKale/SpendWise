package com.mj.spendwise.ui.screens.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mj.spendwise.ui.components.PlaceholderScreen
import com.mj.spendwise.viewmodel.ExpenseViewModel

/** Budget, notification and sync settings are added in later phases. */
@Composable
fun SettingsScreen(vm: ExpenseViewModel, modifier: Modifier = Modifier) {
    val uid by vm.uid.collectAsStateWithLifecycle()
    var budget by rememberSaveable { mutableStateOf("30000") }
    var showDialog by rememberSaveable { mutableStateOf(false) }

    PlaceholderScreen(
        "Settings",
        "Monthly budget: ₹$budget (saved to Firestore in a later phase).\nFirebase uid: ${uid ?: "not signed in"}",
        modifier
    ) {
        Button(onClick = { showDialog = true }) { Text("Edit monthly budget") }
        OutlinedButton(onClick = { vm.reseedDemoData() }) { Text("Reseed demo data") }
    }

    // Budget-edit dialog (pattern 6 in 8.1)
    if (showDialog) {
        var draft by rememberSaveable { mutableStateOf(budget) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Monthly budget") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.filter(Char::isDigit) },
                    label = { Text("Amount (₹)") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = { if (draft.isNotBlank()) budget = draft; showDialog = false }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } }
        )
    }
}
