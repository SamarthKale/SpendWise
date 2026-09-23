package com.mj.spendwise.notifications

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.mj.spendwise.backend.AlertItem
import com.mj.spendwise.backend.FirestoreRepository
import com.mj.spendwise.util.toTimestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Runs every 15 minutes (WorkManager's minimum, fine for a demo). After 8 PM, if no expense was logged today,
 * it creates ONE "daily reminder" alert per day (deduplicated by date) and shows a notification.
 */
class DailyReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            if (LocalDateTime.now().hour < 20) return Result.success()
            if (!FirestoreRepository.isFirebaseConfigured(applicationContext)) return Result.success()
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()

            val today = LocalDate.now()
            val key = "daily_reminder_$today"
            val repo = FirestoreRepository(uid)
            withContext(Dispatchers.IO) {
                val loggedToday = repo.getExpensesSinceBlocking(today.atStartOfDay().toTimestamp())
                if (loggedToday.isEmpty() && !repo.alertExistsBlocking(key)) {
                    val alert = AlertItem().apply {
                        type = "daily_reminder"
                        dedupeKey = key
                        title = "Log today's expenses"
                        severity = "info"
                        body = "You haven't logged any expense today. (AI-generated, demo rules)"
                        payloadJson = "{\n  \"rule\": \"daily_reminder\",\n  \"date\": \"$today\"\n}"
                    }
                    repo.addAlert(alert)
                    NotificationHelper.show(applicationContext, key.hashCode(), alert.title, alert.body)
                }
            }
        } catch (e: Exception) {
            Log.w("DailyReminderWorker", "Skipped this run", e) // never fail the whole worker over a network hiccup
        }
        return Result.success()
    }
}
