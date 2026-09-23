package com.mj.spendwise.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.mj.spendwise.backend.Expense
import com.mj.spendwise.categoryColor
import com.mj.spendwise.util.formatInr

/** One expense row: colour dot, merchant, category, amount, and a cloud icon while the write is pending. */
@Composable
fun ExpenseCard(expense: Expense, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        leadingContent = {
            Box(Modifier.size(14.dp).clip(CircleShape).background(categoryColor(expense.category)))
        },
        headlineContent = { Text(expense.merchant.ifBlank { "(no merchant)" }) },
        supportingContent = {
            val note = if (expense.notes.isNullOrBlank()) "" else " · ${expense.notes}"
            Text(expense.category + note, maxLines = 1)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (expense.pending) {
                    Icon(
                        Icons.Default.CloudUpload,
                        contentDescription = "Pending sync",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
                Text(
                    formatInr(expense.amount),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
    )
}
