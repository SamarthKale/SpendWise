package com.mj.spendwise.ui.screens.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mj.spendwise.notifications.NotificationHelper
import com.mj.spendwise.util.formatInr
import com.mj.spendwise.viewmodel.AlertsViewModel
import com.mj.spendwise.viewmodel.ExpenseViewModel

@Composable
fun SettingsScreen(vm: ExpenseViewModel, alertsVm: AlertsViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val uid by vm.uid.collectAsStateWithLifecycle()
    val wifiOnly by vm.wifiOnly.collectAsStateWithLifecycle()
    val budget by vm.budget.collectAsStateWithLifecycle()
    var showBudget by rememberSaveable { mutableStateOf(false) }
    var notificationsOn by remember { mutableStateOf(NotificationHelper.isEnabled(context)) }
    var permissionNote by remember { mutableStateOf<String?>(null) }
    var afterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Android 13+ runtime permission, asked in context (turning alerts on, sending a test, first budget save).
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            permissionNote = null
            afterPermission?.invoke()
        } else {
            permissionNote = "Notification permission denied. Alerts still appear inside the app."
        }
        afterPermission = null
    }
    fun withNotificationPermission(action: () -> Unit) {
        if (NotificationHelper.hasPermission(context)) {
            action()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            afterPermission = action
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Budget", style = MaterialTheme.typography.titleMedium)
        Text("Monthly budget: ${formatInr(budget.monthlyBudget)}")
        Button(onClick = { showBudget = true }) { Text("Edit budgets") }

        Text("Notifications", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Alert notifications (AI-generated, demo rules)", modifier = Modifier.weight(1f))
            Switch(checked = notificationsOn, onCheckedChange = { on ->
                notificationsOn = on
                NotificationHelper.setEnabled(context, on)
                if (on) withNotificationPermission { }
            })
        }
        OutlinedButton(onClick = { withNotificationPermission { alertsVm.sendTestNotification() } }) {
            Text("Send test notification")
        }
        permissionNote?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        Text("Sync", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Sync on Wi-Fi only", modifier = Modifier.weight(1f))
            Switch(checked = wifiOnly, onCheckedChange = { vm.setWifiOnly(it) })
        }
        OutlinedButton(onClick = { vm.reseedDemoData() }) { Text("Reseed demo data") }

        Text("About", style = MaterialTheme.typography.titleMedium)
        Text("SpendWise ${appVersion(context)}", style = MaterialTheme.typography.bodySmall)
        Text("Firebase uid: ${uid ?: "not signed in"}", style = MaterialTheme.typography.bodySmall)
    }

    if (showBudget) {
        BudgetDialog(
            monthly = budget.monthlyBudget,
            categoryBudgets = budget.categoryBudgets.orEmpty(),
            onSave = { monthly, cats ->
                vm.saveBudget(monthly, cats)
                showBudget = false
                if (notificationsOn) withNotificationPermission { } // first budget set: ask contextually
            },
            onDismiss = { showBudget = false }
        )
    }
}

private fun appVersion(context: android.content.Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
} catch (e: Exception) {
    "1.0"
}
