package com.spendwise.ui.screens.expenses

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Placeholder list: real Firestore data arrives in Phase 2. Tapping passes an id to the detail screen. */
@Composable
fun ExpenseListScreen(onOpenExpense: (String) -> Unit, modifier: Modifier = Modifier) {
    val fakeIds = listOf("demo-1", "demo-2", "demo-3")
    LazyColumn(modifier.fillMaxSize()) {
        items(fakeIds) { id ->
            ListItem(
                headlineContent = { Text("Sample expense ($id)") },
                supportingContent = { Text("Tap to open detail with argument expenseId=$id") },
                modifier = Modifier.clickable { onOpenExpense(id) }
            )
        }
    }
}
