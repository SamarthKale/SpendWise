package com.mj.spendwise.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.mj.spendwise.backend.LocalDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import com.mj.spendwise.backend.AlertItem
import com.mj.spendwise.backend.Expense
import com.mj.spendwise.backend.FirestoreRepository
import com.mj.spendwise.ml.AnomalyDetector
import com.mj.spendwise.notifications.AlertRules
import com.mj.spendwise.notifications.NotificationHelper
import com.mj.spendwise.util.formatInr
import com.mj.spendwise.util.formatInrWhole
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Alerts feed + rule engine trigger. Every alert is (a) saved to Firestore so it syncs, (b) shown as a system
 * notification, (c) listed in the Alerts screen. Rules run after each expense add/edit the user makes
 * (not when data is merely loaded), so seeded demo data doesn't spam notifications.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlertsViewModel(private val app: Application) : AndroidViewModel(app) {
    private val repo = MutableStateFlow<FirestoreRepository?>(null)
    private var expenseVm: ExpenseViewModel? = null

    // Local SQLite copy of the alerts: shown at startup until the live Firestore list arrives.
    private val local = LocalDatabase.get(app)
    private val cachedAlerts = MutableStateFlow<List<AlertItem>?>(null)

    // Eagerly: the dedupe check needs the current alert list even when the Alerts screen isn't open.
    private val liveAlerts: StateFlow<List<AlertItem>?> = repo
        .flatMapLatest { r -> r?.observeAlerts()?.asFlow() ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val alerts: StateFlow<List<AlertItem>?> = combine(liveAlerts, cachedAlerts) { live, cached -> live ?: cached }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch(Dispatchers.IO) { cachedAlerts.value = local.getAlerts().takeIf { it.isNotEmpty() } }
        viewModelScope.launch(Dispatchers.IO) { // write-through on every live change
            liveAlerts.filterNotNull().collect { list ->
                try {
                    local.replaceAlerts(list)
                } catch (e: Exception) {
                    Log.w("AlertsViewModel", "SQLite refresh failed", e)
                }
            }
        }
    }

    val unreadCount: StateFlow<Int> = alerts
        .map { list -> list.orEmpty().count { !it.read } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Connects to the shared ExpenseViewModel once (safe to call repeatedly). */
    fun attach(vm: ExpenseViewModel) {
        if (expenseVm != null) return
        expenseVm = vm
        vm.onExpenseSaved = { saved -> onExpenseSaved(saved) }
        viewModelScope.launch { vm.repository.collect { repo.value = it } }
        // Signed out: forget the previous user's cached alerts too.
        viewModelScope.launch { vm.uid.collect { if (it == null) cachedAlerts.value = null } }
    }

    private fun onExpenseSaved(saved: Expense) {
        val vm = expenseVm ?: return
        viewModelScope.launch {
            delay(600) // let the local snapshot include the new expense
            val list = vm.expenses.value.orEmpty().let { l -> if (l.any { it.id == saved.id }) l else l + saved }
            fire(AlertRules.evaluate(list, vm.budget.value.monthlyBudget, LocalDate.now(), justSaved = saved))
        }
    }

    /** Saves and notifies only alerts whose dedupeKey doesn't exist yet. */
    private fun fire(candidates: List<AlertItem>) {
        val r = repo.value ?: return
        val existing = alerts.value ?: return // not loaded yet: skip rather than risk duplicates
        val known = existing.map { it.dedupeKey ?: it.id }.toSet()
        candidates.filter { it.dedupeKey !in known }.forEach { alert ->
            r.addAlert(alert)
            NotificationHelper.show(app, alert.dedupeKey.hashCode(), alert.title, alert.body)
        }
    }

    fun clearAll() {
        repo.value?.deleteAlerts(alerts.value.orEmpty().map { it.id })
    }

    fun markRead(alert: AlertItem) {
        if (!alert.read) repo.value?.markAlertRead(alert.id)
    }

    /** "Trigger demo alert": a realistic anomaly alert immediately (unique key, so it always fires). */
    fun triggerDemoAlert() {
        val r = repo.value ?: return
        val vm = expenseVm
        val top = vm?.expenses?.value?.let { AnomalyDetector.detect(it, LocalDate.now()).firstOrNull { a -> a.kind == "category" } }
        val alert = AlertItem().apply {
            type = "anomaly"
            dedupeKey = "demo_${System.currentTimeMillis()}"
            severity = "warning"
            if (top != null) {
                title = "Unusual ${top.category} spending"
                body = "${top.category} is ${formatInr(top.currentValue)} this month vs a usual ${formatInrWhole(top.expectedValue)}. (AI-generated, demo rules)"
                payloadJson = "{\n  \"rule\": \"anomaly\",\n  \"category\": \"${top.category}\",\n  \"current\": ${top.currentValue},\n  \"expected\": ${top.expectedValue}\n}"
            } else {
                title = "Unusual spending detected"
                body = "Your spending looks higher than usual. (AI-generated, demo rules)"
                payloadJson = "{\n  \"rule\": \"anomaly\",\n  \"demo\": true\n}"
            }
        }
        r.addAlert(alert)
        NotificationHelper.show(app, alert.dedupeKey.hashCode(), alert.title, alert.body)
    }

    fun sendTestNotification() {
        NotificationHelper.show(app, 1001, "Test notification", "SpendWise alerts are working. (AI-generated, demo rules)")
    }
}
