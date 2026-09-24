package com.mj.spendwise.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.mj.spendwise.backend.LocalDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import com.mj.spendwise.backend.AuthManager
import com.mj.spendwise.backend.Callback
import com.mj.spendwise.backend.Expense
import com.mj.spendwise.backend.FirestoreRepository
import com.mj.spendwise.data.DemoData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.mj.spendwise.backend.BudgetConfig
import com.mj.spendwise.backend.ConnectivityMonitor
import com.mj.spendwise.ml.Anomaly
import com.mj.spendwise.ml.AnomalyDetector
import com.mj.spendwise.ml.InsightsEngine
import java.time.LocalDate
import com.mj.spendwise.backend.SyncStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    // ---- local SQLite copy (lab outcome 3): shown instantly at startup, refreshed from every cloud snapshot ----
    private val local = LocalDatabase.get(app)
    private val cachedExpenses = MutableStateFlow<List<Expense>?>(null)

    /** The single Firestore listener (live cloud data). null until the first snapshot arrives. */
    private val liveExpenses: StateFlow<List<Expense>?> = repo
        .flatMapLatest { r -> r?.observeExpenses()?.asFlow() ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * What every screen shows: live cloud data once it has arrived, the SQLite copy until then.
     * null = nothing yet (first ever launch, still loading); otherwise newest first.
     */
    val expenses: StateFlow<List<Expense>?> = combine(liveExpenses, cachedExpenses) { live, cached -> live ?: cached }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ---- budget + analytics (no hardcoded numbers: everything is computed from the live expense list) ----

    private val _budget = MutableStateFlow(BudgetConfig())
    val budget: StateFlow<BudgetConfig> = _budget.asStateFlow()

    /** Recomputed whenever the expenses or the budget change, so the dashboard updates instantly. */
    val engine: StateFlow<InsightsEngine?> = combine(expenses, budget) { list, b ->
        list?.let { InsightsEngine(it, LocalDate.now(), b.monthlyBudget) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val anomalies: StateFlow<List<Anomaly>> = expenses
        .map { list -> if (list == null) emptyList() else AnomalyDetector.detect(list, LocalDate.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveBudget(monthly: Double, categoryBudgets: Map<String, Double>) {
        val b = BudgetConfig().apply {
            monthlyBudget = monthly
            setCategoryBudgets(HashMap(categoryBudgets))
        }
        _budget.value = b
        repo.value?.saveBudget(b)
        viewModelScope.launch(Dispatchers.IO) { local.saveBudget(b) }
    }

    // ---- connectivity + sync status (lab outcome 5) ----

    private val connectivity = ConnectivityMonitor(app)
    private val whileSubscribed = SharingStarted.WhileSubscribed(5_000)

    val isOnline: StateFlow<Boolean> =
        connectivity.isOnline().asFlow().stateIn(viewModelScope, whileSubscribed, true)

    /** "Wi-Fi", "Mobile data" or "Offline". */
    val networkType: StateFlow<String> =
        connectivity.networkType.asFlow().stateIn(viewModelScope, whileSubscribed, "Wi-Fi")

    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _wifiOnly = MutableStateFlow(prefs.getBoolean(KEY_WIFI_ONLY, false))
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    /** True while "Sync on Wi-Fi only" is on and we're on mobile data: Firestore's network is switched off. */
    val syncPaused: StateFlow<Boolean> = combine(networkType, wifiOnly) { type, only ->
        only && type == "Mobile data"
    }.stateIn(viewModelScope, whileSubscribed, false)

    /** Writes not yet acknowledged by the server, counted from the same single listener as the list. */
    val pendingCount: StateFlow<Int> = expenses
        .map { list -> FirestoreRepository.countPending(list) }
        .stateIn(viewModelScope, whileSubscribed, 0)

    val syncStatus: StateFlow<SyncStatus> = combine(isOnline, syncPaused, pendingCount) { online, paused, pending ->
        FirestoreRepository.computeSyncStatus(online && !paused, pending)
    }.stateIn(viewModelScope, whileSubscribed, SyncStatus.SYNCED)

    init {
        // 1) Read the SQLite copy on a background thread so the UI has data within milliseconds of launch.
        viewModelScope.launch(Dispatchers.IO) {
            val t0 = System.nanoTime()
            val rows = local.getExpenses()
            cachedExpenses.value = rows.takeIf { it.isNotEmpty() }
            local.getBudget()?.let { _budget.value = it }
            Log.i("ExpenseViewModel", "Loaded ${rows.size} expenses from SQLite in ${(System.nanoTime() - t0) / 1_000_000} ms")
        }
        // 2) Write-through: every live snapshot refreshes the SQLite copy (one transaction, off the main thread).
        viewModelScope.launch(Dispatchers.IO) {
            liveExpenses.filterNotNull().collect { list ->
                try {
                    local.replaceExpenses(list)
                } catch (e: Exception) {
                    Log.w("ExpenseViewModel", "SQLite refresh failed (cache only, app keeps working)", e)
                }
            }
        }
        start()
        // Apply "Sync on Wi-Fi only": disable Firestore's network on mobile data, re-enable otherwise.
        viewModelScope.launch {
            syncPaused.collect { paused ->
                if (FirestoreRepository.isFirebaseConfigured(app)) FirestoreRepository.setNetworkEnabled(!paused)
            }
        }
    }

    fun setWifiOnly(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY, enabled).apply()
        _wifiOnly.value = enabled
    }

    override fun onCleared() {
        connectivity.stop()
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
                r.getBudget(Callback { b ->
                    if (b != null) { // null = couldn't read (offline): keep the SQLite copy
                        _budget.value = b
                        viewModelScope.launch(Dispatchers.IO) { local.saveBudget(b) }
                    }
                })
                // Offline: the seed check can't reach the server, so don't keep the splash waiting.
                // (Cached data still shows; seeding is retried on the next online launch.)
                if (connectivity.isOnline().value == false) _startup.value = Startup.Ready
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

    /** Read-only access for AlertsViewModel (same repository/uid, no second sign-in). */
    val repository: StateFlow<FirestoreRepository?> = repo.asStateFlow()

    /** Set by AlertsViewModel: called after the user adds/edits an expense so alert rules run right away. */
    var onExpenseSaved: ((Expense) -> Unit)? = null

    fun addExpense(expense: Expense): Boolean {
        val r = repo.value ?: return false
        r.addExpense(expense, null, null)
        onExpenseSaved?.invoke(expense)
        return true
    }

    fun updateExpense(expense: Expense): Boolean {
        val r = repo.value ?: return false
        r.updateExpense(expense, null)
        onExpenseSaved?.invoke(expense)
        return true
    }

    fun deleteExpense(id: String) {
        repo.value?.deleteExpense(id, null)
    }

    /** Undo for swipe-delete: writes the same document (same id) back (no alert evaluation needed). */
    fun restoreExpense(expense: Expense) {
        repo.value?.updateExpense(expense, null)
    }

    fun reseedDemoData() {
        val r = repo.value ?: return
        r.seedDemoData(DemoData.load(getApplication()), true, Callback { })
    }

    private companion object {
        const val KEY_WIFI_ONLY = "wifi_only"
    }
}
