package com.spendwise.ui.screens.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.CATEGORIES
import com.spendwise.backend.Expense
import com.spendwise.ui.components.CategoryChip
import com.spendwise.ui.components.EmptyState
import com.spendwise.ui.components.ExpenseCard
import com.spendwise.ui.components.LoadingState
import com.spendwise.util.localDate
import com.spendwise.util.relativeLabel
import com.spendwise.viewmodel.ExpenseViewModel
import kotlinx.coroutines.launch

/** Live list from Firestore: search, category filter, grouped by date, swipe left to delete (with Undo). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseListScreen(
    vm: ExpenseViewModel,
    onOpenExpense: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val expenses by vm.expenses.collectAsStateWithLifecycle()
    val startup by vm.startup.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val all = expenses
    val filtered = remember(all, query, category) {
        all.orEmpty().filter { e ->
            (category == null || e.category == category) &&
                (query.isBlank() || e.merchant.contains(query, ignoreCase = true) ||
                    e.notes.orEmpty().contains(query, ignoreCase = true))
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search merchant or notes") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(CATEGORIES) { c ->
                    CategoryChip(c, selected = category == c, onClick = { category = if (category == c) null else c })
                }
            }

            when {
                startup is ExpenseViewModel.Startup.Failed ->
                    EmptyState("Cloud unavailable", (startup as ExpenseViewModel.Startup.Failed).message)
                all == null -> LoadingState()
                all.isEmpty() -> EmptyState("No expenses yet", "Tap + to add your first expense.")
                filtered.isEmpty() -> EmptyState("No matches", "Try a different search or category.")
                else -> {
                    val groups = filtered.groupBy { it.localDate() } // list is already newest-first
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
                        groups.forEach { (date, dayExpenses) ->
                            item(key = "header-$date") {
                                Text(
                                    date.relativeLabel(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            items(dayExpenses, key = { it.id }) { expense ->
                                SwipeToDeleteRow(expense, onOpenExpense) {
                                    vm.deleteExpense(expense.id)
                                    scope.launch {
                                        snackbar.currentSnackbarData?.dismiss()
                                        val result = snackbar.showSnackbar(
                                            "Deleted ${expense.merchant}", "Undo", duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) vm.restoreExpense(expense)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(end = 88.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteRow(expense: Expense, onOpen: (String) -> Unit, onDelete: () -> Unit) {
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
        if (value == SwipeToDismissBoxValue.EndToStart) {
            onDelete()
            true
        } else false
    })
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    ) {
        // Opaque background so the red delete icon isn't visible through the row.
        Box(Modifier.background(MaterialTheme.colorScheme.background)) {
            ExpenseCard(expense, onClick = { onOpen(expense.id) })
        }
    }
}
