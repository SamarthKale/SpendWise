package com.mj.spendwise.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mj.spendwise.MainActivity
import com.mj.spendwise.R

/** System notifications for AI alerts (channel "spendwise_alerts", high importance). */
object NotificationHelper {
    const val CHANNEL_ID = "spendwise_alerts"
    const val EXTRA_ROUTE = "route"
    private const val PREFS = "settings"
    private const val KEY_ENABLED = "notifications_enabled"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "Spending alerts", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Budget, anomaly and reminder alerts (AI-generated, demo rules)"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** The user's in-app switch (Settings). Separate from the Android system permission. */
    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** Android 13+ needs the POST_NOTIFICATIONS runtime permission; earlier versions don't. */
    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Shows a notification whose tap opens the Alerts screen (deep link). Silently does nothing without permission. */
    fun show(context: Context, id: Int, title: String, body: String) {
        if (!isEnabled(context) || !hasPermission(context)) return
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ROUTE, "alerts")
        }
        val pending = PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            Log.w("NotificationHelper", "Notification blocked", e)
        }
    }
}
