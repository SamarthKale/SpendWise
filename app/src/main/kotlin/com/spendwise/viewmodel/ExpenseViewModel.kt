package com.spendwise.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import com.spendwise.backend.AuthManager
import com.spendwise.backend.Callback
import com.spendwise.backend.Expense
import com.spendwise.backend.FirestoreRepository
import com.spendwise.data.DemoData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * Single source of truth for expenses. Composables never touch Firebase; they observe [expenses]
 * (ONE Firestore listener, shared by every screen) and call the functions below.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseViewModel(app: Application) : AndroidViewModel(app) {

    sealed interface Startup {
        data object Loading : Startup
        data object Ready : Startup
        data class Failed(val message: String) : Startup
    }

    private val _startup = MutableStateFlow<Startup>(Startup.Loading)
    val startup: StateFlow<Startup> = _startup.asStateFlow()

    private val _uid = MutableStateFlow<String?>(null)
    val uid: StateFlow<String?> = _uid.asStateFlow()

    private val repo = MutableStateFlow<FirestoreRepository?>(null)

    /** null = still loading (or Firebase unavailable); otherwise newest first. */
    val expenses: StateFlow<List<Expense>?> = repo
        .flatMapLatest { r -> r?.observeExpenses()?.asFlow() ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        start()
    }

    /** Anonymous sign-in, then seed demo data once. Splash waits for this. */
    private fun start() {
        val context = getApplication<Application>()
        if (!FirestoreRepository.isFirebaseConfigured(context)) {
            _startup.value = Startup.Failed(
                "Firebase is not configured. Add app/google-services.json (see README / DECISIONS.md)."
            )
            return
        }
        AuthManager.signInAnonymouslyIfNeeded(
            Callback { uid ->
                _uid.value = uid
                val r = FirestoreRepository(uid)
                repo.value = r
                r.seedDemoData(DemoData.load(context), false, Callback { _startup.value = Startup.Ready })
            },
            Callback { e ->
                _startup.value = Startup.Failed(
                    "Sign-in failed: ${e.message}. Is Anonymous sign-in enabled in the Firebase console?"
                )
            }
        )
    }

    // ---- write operations: return false when the cloud isn't available ----

    fun addExpense(expense: Expense): Boolean {
        val r = repo.value ?: return false
        r.addExpense(expense, null, null)
        return true
    }

    fun updateExpense(expense: Expense): Boolean {
        val r = repo.value ?: return false
        r.updateExpense(expense, null)
        return true
    }

    fun deleteExpense(id: String) {
        repo.value?.deleteExpense(id, null)
    }

    /** Undo for swipe-delete: writes the same document (same id) back. */
    fun restoreExpense(expense: Expense) {
        updateExpense(expense)
    }

    fun reseedDemoData() {
        val r = repo.value ?: return
        r.seedDemoData(DemoData.load(getApplication()), true, Callback { })
    }
}
