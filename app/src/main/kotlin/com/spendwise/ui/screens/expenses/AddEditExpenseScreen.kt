package com.spendwise.ui.screens.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.CATEGORIES
import com.spendwise.backend.Expense
import com.spendwise.ui.screens.scan.ReceiptScannerSheet
import com.spendwise.util.localDate
import com.spendwise.util.pretty
import com.spendwise.util.toLocalDateTime
import com.spendwise.util.toTimestamp
import com.spendwise.viewmodel.ExpenseViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Add or edit an expense.
 * @param scan true when opened via "Scan receipt": the scanner sheet opens immediately.
 * @param editId non-null when editing an existing expense.
 * Phase 4 adds OCR pre-fill and the GPS toggle; the classifier will auto-pick the category.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditExpenseScreen(
    vm: ExpenseViewModel,
    scan: Boolean,
    editId: String?,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier
) {
    val expenses by vm.expenses.collectAsStateWithLifecycle()
    val existing = remember(editId, expenses) { editId?.let { id -> expenses?.firstOrNull { it.id == id } } }

    // rememberSaveable so the form survives rotation and process death.
    var merchant by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Shopping") }
    var notes by rememberSaveable { mutableStateOf("") }
    var epochDay by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var filled by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var showSheet by rememberSaveable { mutableStateOf(scan) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    // When editing, copy the stored values into the form once (as soon as the expense is loaded).
    LaunchedEffect(existing) {
        val e = existing
        if (e != null && !filled) {
            merchant = e.merchant.orEmpty()
            amount = if (e.amount % 1.0 == 0.0) e.amount.toLong().toString() else e.amount.toString()
            category = e.category
            notes = e.notes.orEmpty()
            epochDay = e.localDate().toEpochDay()
            filled = true
        }
    }

    Column(
        modifier.verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(onClick = { showSheet = true }, Modifier.fillMaxWidth()) { Text("Scan receipt") }

        OutlinedTextField(
            value = merchant, onValueChange = { merchant = it },
            label = { Text("Merchant") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = amount,
            onValueChange = { input -> amount = input.filter { it.isDigit() || it == '.' } },
            label = { Text("Amount (₹)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        ExposedDropdownMenuBox(expanded = menuOpen, onExpandedChange = { menuOpen = it }) {
            OutlinedTextField(
                value = category, onValueChange = {}, readOnly = true,
                label = { Text("Category") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuOpen) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                CATEGORIES.forEach { c ->
                    DropdownMenuItem(text = { Text(c) }, onClick = { category = c; menuOpen = false })
                }
            }
        }

        OutlinedButton(onClick = { showDatePicker = true }, Modifier.fillMaxWidth()) {
            Text("Date: ${LocalDate.ofEpochDay(epochDay).pretty()}")
        }
        OutlinedTextField(
            value = notes, onValueChange = { notes = it },
            label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth()
        )

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(
            onClick = {
                val value = amount.toDoubleOrNull()
                when {
                    merchant.isBlank() -> error = "Please enter a merchant."
                    value == null || value <= 0.0 -> error = "Please enter an amount greater than 0."
                    else -> {
                        val chosen = LocalDate.ofEpochDay(epochDay)
                        val expense = (existing?.copy() ?: Expense()).apply {
                            this.merchant = merchant.trim()
                            this.amount = value
                            this.category = category
                            this.notes = notes.trim()
                            // Keep the original time of day when editing; use "now" for new expenses.
                            if (existing == null || existing.localDate() != chosen) {
                                val time = existing?.timestamp?.toLocalDateTime()?.toLocalTime() ?: LocalTime.now()
                                timestamp = chosen.atTime(time).toTimestamp()
                            }
                        }
                        val ok = if (existing == null) vm.addExpense(expense) else vm.updateExpense(expense)
                        if (ok) onSaved() else error = "Cloud is not available, so this can't be saved."
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (editId == null) "Save expense" else "Save changes") }
    }

    if (showSheet) ReceiptScannerSheet(onDismiss = { showSheet = false })

    if (showDatePicker) {
        // The picker works in UTC midnight millis; convert to/from a plain LocalDate.
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = epochDay * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        epochDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = pickerState) }
    }
}
