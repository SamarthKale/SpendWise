package com.mj.spendwise

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mj.spendwise.navigation.SpendWiseNavHost
import com.mj.spendwise.notifications.NotificationHelper
import com.mj.spendwise.ui.theme.SpendWiseTheme

/** Single Activity; all screens are Compose destinations. */
class MainActivity : ComponentActivity() {
    // Set when a notification is tapped: the nav host opens that screen (deep link to Alerts).
    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingRoute = intent?.getStringExtra(NotificationHelper.EXTRA_ROUTE)
        setContent {
            SpendWiseTheme {
                SpendWiseNavHost(pendingRoute = pendingRoute, onRouteHandled = { pendingRoute = null })
            }
        }
    }

    // launchMode="singleTop": a notification tap while the app is open arrives here.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoute = intent.getStringExtra(NotificationHelper.EXTRA_ROUTE)
    }
}
