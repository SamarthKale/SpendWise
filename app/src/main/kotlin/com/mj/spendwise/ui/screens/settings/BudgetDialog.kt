package com.mj.spendwise.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.mj.spendwise.CATEGORIES

/** Budget-edit dialog (pattern 6 in 8.1): monthly budget plus optional per-category budgets (blank = none). */
@Composable
fun BudgetDialog(
    monthly: Double,
    categoryBudgets: Map<String, Double>,
    onSave: (Double, Map<String, Double>) -> Unit,
    onDismiss: () -> Unit
) {
    var monthlyText by rememberSaveable { mutableStateOf(monthly.toLong().toString()) }
    val categoryText = remember { mutableStateMapOf<String, String>().apply {
        CATEGORIES.forEach { c -> put(c, categoryBudgets[c]?.toLong()?.toString().orEmpty()) }
    } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Budgets") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = monthlyText,
                    onValueChange = { monthlyText = it.filter(Char::isDigit) },
                    label = { Text("Monthly budget (₹)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Category budgets (optional)", style = MaterialTheme.typography.labelLarge)
                CATEGORIES.forEach { c ->
                    OutlinedTextField(
                        value = categoryText[c].orEmpty(),
                        onValueChange = { categoryText[c] = it.filter(Char::isDigit) },
                        label = { Text(c) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val m = monthlyText.toDoubleOrNull()?.takeIf { it > 0 } ?: monthly
                val cats = categoryText.mapNotNull { (k, v) -> v.toDoubleOrNull()?.takeIf { it > 0 }?.let { k to it } }.toMap()
                onSave(m, cats)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
