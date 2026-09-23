package com.spendwise.ui.screens.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.spendwise.ui.components.PlaceholderScreen

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    var budget by rememberSaveable { mutableStateOf("30000") }
    var showDialog by rememberSaveable { mutableStateOf(false) }

    PlaceholderScreen("Settings", "Monthly budget: ₹$budget (saved to Firestore in a later phase).", modifier) {
        Button(onClick = { showDialog = true }) { Text("Edit monthly budget") }
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
