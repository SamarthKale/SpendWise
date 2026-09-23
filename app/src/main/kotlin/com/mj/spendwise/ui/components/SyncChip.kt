package com.mj.spendwise.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mj.spendwise.backend.SyncStatus

/**
 * Shows Synced / Syncing / Offline. [compact] is the smaller variant used in the top bar.
 * Status colours: green = synced, amber = syncing, red = offline.
 */
@Composable
fun SyncChip(status: SyncStatus, pending: Int, modifier: Modifier = Modifier, compact: Boolean = false) {
    val (icon, color) = when (status) {
        SyncStatus.SYNCED -> Icons.Default.CloudDone to Color(0xFF2E7D32)
        SyncStatus.SYNCING -> Icons.Default.Sync to Color(0xFFEF8F00)
        SyncStatus.OFFLINE -> Icons.Default.CloudOff to MaterialTheme.colorScheme.error
    }
    val label = when (status) {
        SyncStatus.SYNCED -> "Synced ✓"
        SyncStatus.SYNCING -> if (compact) "Syncing" else "Syncing · $pending pending"
        SyncStatus.OFFLINE -> if (pending > 0 && !compact) "Offline · $pending pending" else "Offline"
    }
    AssistChip(
        onClick = {},
        modifier = modifier,
        label = {
            Text(label, style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge)
        },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(if (compact) 14.dp else 18.dp)) },
        colors = AssistChipDefaults.assistChipColors(labelColor = color, leadingIconContentColor = color)
    )
}
