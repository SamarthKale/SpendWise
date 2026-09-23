package com.mj.spendwise.ui.screens.expenses

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mj.spendwise.CATEGORIES
import com.mj.spendwise.backend.Expense
import com.mj.spendwise.location.LocationProvider
import com.mj.spendwise.ml.CategoryClassifier
import com.mj.spendwise.ui.screens.scan.ReceiptScannerSheet
import com.mj.spendwise.util.localDate
import com.mj.spendwise.util.pretty
import com.mj.spendwise.util.toLocalDateTime
import com.mj.spendwise.util.toTimestamp
import com.mj.spendwise.viewmodel.ExpenseViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Add or edit an expense.
 * @param scan true when opened via "Scan receipt": the scanner sheet opens immediately.
 * @param editId non-null when editing an existing expense.
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val expenses by vm.expenses.collectAsStateWithLifecycle()
    val existing = remember(editId, expenses) { editId?.let { id -> expenses?.firstOrNull { it.id == id } } }

    // rememberSaveable so the form survives rotation and process death.
    var merchant by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Shopping") }
    var categoryTouched by rememberSaveable { mutableStateOf(false) } // user picked one: stop auto-suggesting
    var notes by rememberSaveable { mutableStateOf("") }
    var source by rememberSaveable { mutableStateOf("manual") }
    var epochDay by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var filled by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var showSheet by rememberSaveable { mutableStateOf(scan) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    // GPS attach (lab outcome 7)
    val locationProvider = remember { LocationProvider(context) }
    var attach by rememberSaveable { mutableStateOf(false) }
    var lat by rememberSaveable { mutableStateOf<Double?>(null) }
    var lng by rememberSaveable { mutableStateOf<Double?>(null) }
    var locName by rememberSaveable { mutableStateOf<String?>(null) }
    var locStatus by rememberSaveable { mutableStateOf<String?>(null) }

    fun fetchLocation() {
        scope.launch {
            locStatus = "Getting your location…"
            val point = locationProvider.getCurrentLocation()
            if (point == null) {
                attach = false
                locStatus = "Couldn't get a location fix (is GPS on?). Saving without location."
            } else {
                lat = point.lat
                lng = point.lng
                locName = locationProvider.reverseGeocode(point)
                locStatus = locName ?: "%.4f, %.4f".format(point.lat, point.lng)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) fetchLocation()
        else {
            attach = false
            locStatus = "Location permission denied — continuing without location."
        }
    }

    // When editing, copy the stored values into the form once (as soon as the expense is loaded).
    LaunchedEffect(existing) {
        val e = existing
        if (e != null && !filled) {
            merchant = e.merchant.orEmpty()
            amount = if (e.amount % 1.0 == 0.0) e.amount.toLong().toString() else e.amount.toString()
            category = e.category
            categoryTouched = true
            notes = e.notes.orEmpty()
            source = e.source ?: "manual"
            epochDay = e.localDate().toEpochDay()
            filled = true
        }
    }

    // Auto-select the category while typing the merchant (keyword classifier).
    val suggestion = CategoryClassifier.classify(merchant, notes)
    LaunchedEffect(suggestion) {
        if (!categoryTouched && suggestion != null) category = suggestion
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
                supportingText = { Text("Suggested: ${suggestion ?: "—"}") },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                CATEGORIES.forEach { c ->
                    DropdownMenuItem(text = { Text(c) }, onClick = {
                        category = c
                        categoryTouched = true
                        menuOpen = false
                    })
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

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Switch(checked = attach, onCheckedChange = { on ->
                attach = on
                if (!on) {
                    lat = null; lng = null; locName = null; locStatus = null
                } else if (locationProvider.hasPermission()) {
                    fetchLocation()
                } else {
                    permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            })
            Column {
                Text("Attach current location")
                locStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }

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
                            this.source = source
                            if (attach && lat != null && lng != null) {
                                latitude = lat
                                longitude = lng
                                locationName = locName.orEmpty()
                            }
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

    if (showSheet) {
        ReceiptScannerSheet(
            onDismiss = { showSheet = false },
            onConfirm = { r ->
                r.merchant?.let { merchant = it }
                r.amount?.let { amount = if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }
                r.category?.let { category = it; categoryTouched = true }
                source = "receipt_scan"
                showSheet = false
            }
        )
    }

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
