package com.mj.spendwise.backend;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.PersistentCacheSettings;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * All Firestore access lives here. Data layout:
 *   users/{uid}/expenses/{id}, users/{uid}/alerts/{id}, users/{uid}/settings/budget|meta
 *
 * Offline: Firestore's persistent cache keeps a local copy and queues writes. Write calls therefore
 * report success right after the LOCAL commit (the server ack can be hours away when offline).
 */
public class FirestoreRepository {
    private static final String TAG = "FirestoreRepository";

    private final FirebaseFirestore db;
    private final String uid;

    public FirestoreRepository(String uid) {
        this.db = FirebaseFirestore.getInstance();
        this.uid = uid;
    }

    // ---------- setup helpers (called from the Application class) ----------

    /** False when google-services.json is missing, so callers can show a friendly message. */
    public static boolean isFirebaseConfigured(Context context) {
        return !FirebaseApp.getApps(context).isEmpty();
    }

    /** Turns on the on-device cache (this is the app's "local database" and offline queue). */
    public static void enablePersistentCache() {
        try {
            FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                    .build();
            FirebaseFirestore.getInstance().setFirestoreSettings(settings);
        } catch (Exception e) {
            Log.w(TAG, "Could not enable persistent cache", e);
        }
    }

    /** Used by "Sync on Wi-Fi only": pauses/resumes Firestore's network connection (cache keeps working). */
    public static void setNetworkEnabled(boolean enabled) {
        try {
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            (enabled ? db.enableNetwork() : db.disableNetwork())
                    .addOnFailureListener(e -> Log.w(TAG, "setNetworkEnabled failed", e));
        } catch (Exception e) {
            Log.w(TAG, "setNetworkEnabled failed", e);
        }
    }

    // ---------- sync status (derived from the single expenses listener, no extra listeners) ----------

    /** Number of expenses whose write hasn't been acknowledged by the server yet. */
    public static int countPending(List<Expense> expenses) {
        int n = 0;
        if (expenses != null) {
            for (Expense e : expenses) if (e.getPending()) n++;
        }
        return n;
    }

    /** Offline wins; otherwise pending writes mean SYNCING; otherwise everything is on the server. */
    public static SyncStatus computeSyncStatus(boolean online, int pendingWrites) {
        if (!online) return SyncStatus.OFFLINE;
        return pendingWrites > 0 ? SyncStatus.SYNCING : SyncStatus.SYNCED;
    }

    // ---------- paths ----------

    private DocumentReference userDoc() { return db.collection("users").document(uid); }
    private CollectionReference expenses() { return userDoc().collection("expenses"); }
    private CollectionReference alerts() { return userDoc().collection("alerts"); }
    private DocumentReference budgetDoc() { return userDoc().collection("settings").document("budget"); }
    private DocumentReference metaDoc() { return userDoc().collection("settings").document("meta"); }

    // ---------- expenses ----------

    /** One realtime listener; metadata changes are included so "pending" flags update on server ack. */
    public LiveData<List<Expense>> observeExpenses() {
        Query q = expenses().orderBy("timestamp", Query.Direction.DESCENDING);
        return new QueryLiveData<>(q, doc -> {
            Expense e = doc.toObject(Expense.class);
            if (e == null) return null;
            e.setId(doc.getId());
            e.setPending(doc.getMetadata().hasPendingWrites());
            return e;
        });
    }

    public void addExpense(Expense expense, Callback<String> ok, Callback<Exception> err) {
        DocumentReference ref = expenses().document();
        expense.setId(ref.getId());
        // Do NOT wait for this task: offline it only completes once the server acknowledges.
        ref.set(expense).addOnFailureListener(e -> {
            Log.w(TAG, "addExpense failed on server", e);
            if (err != null) err.onResult(e);
        });
        if (ok != null) ok.onResult(ref.getId());
    }

    /** Replaces the whole document (also used by "Undo delete", which re-creates it with the same id). */
    public void updateExpense(Expense expense, Callback<Exception> err) {
        expenses().document(expense.getId()).set(expense).addOnFailureListener(e -> {
            Log.w(TAG, "updateExpense failed", e);
            if (err != null) err.onResult(e);
        });
    }

    public void deleteExpense(String id, Callback<Exception> err) {
        expenses().document(id).delete().addOnFailureListener(e -> {
            Log.w(TAG, "deleteExpense failed", e);
            if (err != null) err.onResult(e);
        });
    }

    // ---------- alerts (used from Phase 8) ----------

    public LiveData<List<AlertItem>> observeAlerts() {
        Query q = alerts().orderBy("createdAt", Query.Direction.DESCENDING);
        return new QueryLiveData<>(q, doc -> {
            AlertItem a = doc.toObject(AlertItem.class);
            if (a == null) return null;
            a.setId(doc.getId());
            return a;
        });
    }

    /** Uses dedupeKey as the document id when present, so the same alert is never written twice. */
    public void addAlert(AlertItem alert) {
        String key = alert.getDedupeKey();
        DocumentReference ref = (key != null && !key.isEmpty()) ? alerts().document(key) : alerts().document();
        alert.setId(ref.getId());
        ref.set(alert).addOnFailureListener(e -> Log.w(TAG, "addAlert failed", e));
    }

    /** Deletes the given alerts in one batch ("Clear all" on the Alerts screen). */
    public void deleteAlerts(List<String> ids) {
        if (ids.isEmpty()) return;
        WriteBatch batch = db.batch();
        for (String id : ids) batch.delete(alerts().document(id));
        batch.commit().addOnFailureListener(e -> Log.w(TAG, "deleteAlerts failed", e));
    }

    public void markAlertRead(String id) {
        alerts().document(id).update("read", true)
                .addOnFailureListener(e -> Log.w(TAG, "markAlertRead failed", e));
    }

    // ---------- blocking helpers for background workers (never call these on the main thread) ----------

    /** Expenses with timestamp >= since. Used by the daily-reminder worker. Reads from the cache when offline. */
    public List<Expense> getExpensesSinceBlocking(com.google.firebase.Timestamp since) throws Exception {
        QuerySnapshot snap = Tasks.await(expenses().whereGreaterThanOrEqualTo("timestamp", since).get());
        List<Expense> result = new ArrayList<>();
        for (DocumentSnapshot doc : snap.getDocuments()) {
            Expense e = doc.toObject(Expense.class);
            if (e != null) {
                e.setId(doc.getId());
                result.add(e);
            }
        }
        return result;
    }

    public boolean alertExistsBlocking(String id) throws Exception {
        return Tasks.await(alerts().document(id).get()).exists();
    }

    // ---------- budget ----------

    public void getBudget(Callback<BudgetConfig> cb) {
        budgetDoc().get().addOnSuccessListener(doc -> {
            BudgetConfig b = doc.exists() ? doc.toObject(BudgetConfig.class) : null;
            cb.onResult(b != null ? b : new BudgetConfig());
        }).addOnFailureListener(e -> cb.onResult(null)); // null = could not read (e.g. offline, nothing cached)
    }

    public void saveBudget(BudgetConfig budget) {
        budgetDoc().set(budget).addOnFailureListener(e -> Log.w(TAG, "saveBudget failed", e));
    }

    // ---------- demo data ----------

    /**
     * Writes the demo expenses in one batch. Guarded by a "seeded" flag in settings/meta so it only
     * happens once, unless force is true (Settings > Reseed). Demo docs have fixed ids, so reseeding
     * overwrites them instead of creating duplicates.
     * done gets true if a seed was written.
     */
    public void seedDemoData(List<Expense> demo, boolean force, Callback<Boolean> done) {
        metaDoc().get().addOnSuccessListener(meta -> {
            boolean seeded = meta.exists() && Boolean.TRUE.equals(meta.getBoolean("seeded"));
            if (seeded && !force) {
                done.onResult(false);
                return;
            }
            WriteBatch batch = db.batch();
            for (Expense e : demo) {
                DocumentReference ref = e.getId() != null ? expenses().document(e.getId()) : expenses().document();
                batch.set(ref, e);
            }
            Map<String, Object> flag = new HashMap<>();
            flag.put("seeded", true);
            batch.set(metaDoc(), flag);
            batch.commit().addOnFailureListener(e -> Log.w(TAG, "seed batch failed", e));
            done.onResult(true); // local cache is already updated; don't wait for the server
        }).addOnFailureListener(e -> {
            Log.w(TAG, "Could not check seed flag (offline on first launch?)", e);
            done.onResult(false);
        });
    }

    // ---------- shared snapshot -> LiveData plumbing ----------

    private interface Mapper<T> { T map(DocumentSnapshot doc); }

    /** LiveData that only holds a Firestore listener while someone is observing it. */
    private static class QueryLiveData<T> extends LiveData<List<T>> {
        private final Query query;
        private final Mapper<T> mapper;
        private ListenerRegistration registration;

        QueryLiveData(Query query, Mapper<T> mapper) {
            this.query = query;
            this.mapper = mapper;
        }

        @Override
        protected void onActive() {
            registration = query.addSnapshotListener(MetadataChanges.INCLUDE, (snapshot, error) -> {
                if (error != null) {
                    Log.w(TAG, "Snapshot listener error", error);
                    return;
                }
                if (snapshot == null) return;
                List<T> result = new ArrayList<>();
                for (DocumentSnapshot doc : snapshot.getDocuments()) {
                    try {
                        T item = mapper.map(doc);
                        if (item != null) result.add(item);
                    } catch (RuntimeException ex) {
                        Log.w(TAG, "Skipping malformed document " + doc.getId(), ex); // never crash on bad data
                    }
                }
                setValue(result);
            });
        }

        @Override
        protected void onInactive() {
            if (registration != null) {
                registration.remove();
                registration = null;
            }
        }
    }
}
