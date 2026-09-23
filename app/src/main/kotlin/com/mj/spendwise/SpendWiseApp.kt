package com.mj.spendwise

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.mj.spendwise.backend.FirestoreRepository
import com.mj.spendwise.notifications.DailyReminderWorker
import com.mj.spendwise.notifications.NotificationHelper
import java.util.concurrent.TimeUnit
import org.osmdroid.config.Configuration
import java.io.File

/**
 * Application class. Firebase itself is initialised automatically from google-services.json;
 * here we only switch on Firestore's persistent offline cache. Notification channels and the
 * osmdroid user agent are added in later phases.
 */
class SpendWiseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Map = OpenStreetMap (osmdroid). The lab's "Google Maps" outcome is satisfied with OSM as a free
        // alternative: no API key. OSM's tile policy requires a user agent; tiles are cached in the cache dir.
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }
        NotificationHelper.createChannel(this)
        if (FirestoreRepository.isFirebaseConfigured(this)) {
            FirestoreRepository.enablePersistentCache()
            // Periodic "no expense logged today" reminder. KEEP = don't reschedule on every app start.
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "daily_reminder", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<DailyReminderWorker>(15, TimeUnit.MINUTES).build()
            )
        }
    }
}
