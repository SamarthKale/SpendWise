package com.mj.spendwise.ui.screens.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mj.spendwise.backend.AlertItem
import com.mj.spendwise.ui.components.EmptyState
import com.mj.spendwise.ui.components.LoadingState
import com.mj.spendwise.util.toLocalDateTime
import com.mj.spendwise.viewmodel.AlertsViewModel
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME_FMT = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH)

/** Newest first. Tap an alert to expand it (shows the payload JSON) and mark it read. */
@Composable
fun AlertsScreen(vm: AlertsViewModel, modifier: Modifier = Modifier) {
    val alerts by vm.alerts.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(setOf<String>()) }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "AI-generated (demo rules)", style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { vm.clearAll() }, enabled = !alerts.isNullOrEmpty()) { Text("Clear all") }
            Button(onClick = { vm.triggerDemoAlert() }) { Text("Trigger demo alert") }
        }
        val list = alerts
        when {
            list == null -> LoadingState()
            list.isEmpty() -> EmptyState(
                "No alerts yet",
                "Add a large expense or tap \"Trigger demo alert\". Alerts also arrive as notifications."
            )
            else -> LazyColumn(
                Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(list, key = { it.id }) { alert ->
                    val open = alert.id in expanded
                    AlertCard(alert, open) {
                        expanded = if (open) expanded - alert.id else expanded + alert.id
                        vm.markRead(alert)
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertCard(alert: AlertItem, open: Boolean, onClick: () -> Unit) {
    val (icon, color) = when (alert.severity) {
        "critical" -> Icons.Default.Error to MaterialTheme.colorScheme.error
        "warning" -> Icons.Default.Warning to Color(0xFFEF8F00)
        else -> Icons.Default.Info to MaterialTheme.colorScheme.primary
    }
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, contentDescription = alert.severity, tint = color)
                Text(
                    alert.title, Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (alert.read) FontWeight.Normal else FontWeight.Bold
                )
                if (!alert.read) Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            }
            Text(alert.body, style = MaterialTheme.typography.bodyMedium, maxLines = if (open) Int.MAX_VALUE else 2)
            Text(alert.createdAt.toLocalDateTime().format(TIME_FMT), style = MaterialTheme.typography.labelSmall)
            if (open) {
                Text("Payload", style = MaterialTheme.typography.labelMedium)
                Text(alert.payloadJson, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
