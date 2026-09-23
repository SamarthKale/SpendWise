package com.mj.spendwise

import android.app.Application
import com.mj.spendwise.backend.FirestoreRepository

/**
 * Application class. Firebase itself is initialised automatically from google-services.json;
 * here we only switch on Firestore's persistent offline cache. Notification channels and the
 * osmdroid user agent are added in later phases.
 */
class SpendWiseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (FirestoreRepository.isFirebaseConfigured(this)) {
            FirestoreRepository.enablePersistentCache()
        }
    }
}
