package com.mj.spendwise.data

import android.content.Context
import com.mj.spendwise.backend.BudgetConfig
import com.mj.spendwise.backend.LocalDatabase
import com.mj.spendwise.backend.LocalProfiles
import com.mj.spendwise.backend.ProfileKeys

/**
 * Creates the local SQLite databases the first time the app runs on a device (every colleague who clones the
 * repo and launches the app gets them automatically; nothing has to be set up by hand):
 *  - spendwise_app.db    the registry of logins on this device
 *  - spendwise_guest.db  the guest profile, ALREADY FILLED with the demo expenses
 * Email accounts are different on purpose: their database is created empty at their first login.
 * Everything here is idempotent, so running it on every launch is safe.
 */
object LocalProvisioner {
    private const val PRE_PROFILE_DB = "spendwise.db" // single shared file used by earlier builds

    /** Called from the Application class on a background thread. */
    fun provisionOnFirstRun(context: Context) {
        context.deleteDatabase(PRE_PROFILE_DB)
        LocalProfiles.get(context).count() // opening the helper creates spendwise_app.db
        ensureGuestDatabase(context)
    }

    /** Guest profile: created and filled with demo data once (dates are shifted to "now" at fill time). */
    @Synchronized
    fun ensureGuestDatabase(context: Context) {
        val db = LocalDatabase.forProfile(context, ProfileKeys.GUEST)
        if (!db.isProvisioned()) {
            db.replaceExpenses(DemoData.load(context))
            db.saveBudget(BudgetConfig())
            db.markProvisioned()
        }
    }

    /** A new guest sign-in is a new anonymous cloud user, so the guest database gets a fresh copy of the demo data. */
    @Synchronized
    fun resetGuestDatabase(context: Context) {
        LocalDatabase.deleteProfile(context, ProfileKeys.GUEST)
        ensureGuestDatabase(context)
    }
}
