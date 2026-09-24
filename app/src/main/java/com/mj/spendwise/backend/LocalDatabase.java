package com.mj.spendwise.backend;

import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;

import com.google.firebase.Timestamp;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Local SQLite 3 database (file: databases/spendwise.db). It keeps an on-device copy of the expenses, alerts
 * and budget so the app can show data INSTANTLY at startup (before Firestore answers) and even with no cloud.
 * Firestore stays the source of truth: every snapshot from the cloud is written here (write-through), and
 * this database is only read to fill the screens until live data arrives.
 *
 * Speed: one transaction + one prepared statement per refresh, indexed by time, called off the main thread.
 * Being a cache, upgrading the schema simply recreates the tables.
 */
public class LocalDatabase extends SQLiteOpenHelper {
    public static final String NAME = "spendwise.db";
    private static final int VERSION = 1;
    private static LocalDatabase instance;

    public static synchronized LocalDatabase get(Context context) {
        if (instance == null) instance = new LocalDatabase(context.getApplicationContext());
        return instance;
    }

    private LocalDatabase(Context context) {
        super(context, NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE expenses (" +
                "id TEXT PRIMARY KEY, merchant TEXT, category TEXT, amount REAL NOT NULL, notes TEXT, " +
                "location_name TEXT, latitude REAL, longitude REAL, timestamp_ms INTEGER NOT NULL, " +
                "source TEXT, saved_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_expenses_time ON expenses(timestamp_ms DESC)");
        db.execSQL("CREATE TABLE alerts (" +
                "id TEXT PRIMARY KEY, title TEXT, body TEXT, type TEXT, severity TEXT, payload_json TEXT, " +
                "dedupe_key TEXT, created_ms INTEGER NOT NULL, is_read INTEGER NOT NULL DEFAULT 0, saved_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_alerts_time ON alerts(created_ms DESC)");
        db.execSQL("CREATE TABLE budget (id INTEGER PRIMARY KEY CHECK (id = 1), monthly REAL NOT NULL, category_json TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS expenses");
        db.execSQL("DROP TABLE IF EXISTS alerts");
        db.execSQL("DROP TABLE IF EXISTS budget");
        onCreate(db);
    }

    /** Wipes every cached row. Called on sign-out so the next user never sees the previous user's data. */
    public void clearAll() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("expenses", null, null);
            db.delete("alerts", null, null);
            db.delete("budget", null, null);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    // ---------------- expenses ----------------

    /** Replaces the whole cached list in one transaction (mirrors the latest Firestore snapshot). */
    public void replaceExpenses(List<Expense> list) {
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        db.beginTransaction();
        try {
            db.delete("expenses", null, null);
            SQLiteStatement st = db.compileStatement("INSERT OR REPLACE INTO expenses " +
                    "(id, merchant, category, amount, notes, location_name, latitude, longitude, timestamp_ms, source, saved_at) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?)");
            for (Expense e : list) {
                if (e.getId() == null) continue;
                st.clearBindings();
                st.bindString(1, e.getId());
                bindText(st, 2, e.getMerchant());
                bindText(st, 3, e.getCategory());
                st.bindDouble(4, e.getAmount());
                bindText(st, 5, e.getNotes());
                bindText(st, 6, e.getLocationName());
                if (e.getLatitude() != null) st.bindDouble(7, e.getLatitude());
                if (e.getLongitude() != null) st.bindDouble(8, e.getLongitude());
                st.bindLong(9, e.getTimestamp().toDate().getTime());
                bindText(st, 10, e.getSource());
                st.bindLong(11, now);
                st.executeInsert();
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** Newest first, same order as the Firestore query. */
    public List<Expense> getExpenses() {
        List<Expense> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, merchant, category, amount, notes, location_name, latitude, longitude, timestamp_ms, source " +
                        "FROM expenses ORDER BY timestamp_ms DESC", null)) {
            while (c.moveToNext()) {
                Expense e = new Expense();
                e.setId(c.getString(0));
                e.setMerchant(c.getString(1));
                e.setCategory(c.getString(2));
                e.setAmount(c.getDouble(3));
                e.setNotes(c.getString(4));
                e.setLocationName(c.getString(5));
                e.setLatitude(c.isNull(6) ? null : c.getDouble(6));
                e.setLongitude(c.isNull(7) ? null : c.getDouble(7));
                e.setTimestamp(new Timestamp(new Date(c.getLong(8))));
                e.setSource(c.getString(9));
                out.add(e);
            }
        }
        return out;
    }

    public long expenseCount() {
        return DatabaseUtils.queryNumEntries(getReadableDatabase(), "expenses");
    }

    // ---------------- alerts ----------------

    public void replaceAlerts(List<AlertItem> list) {
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        db.beginTransaction();
        try {
            db.delete("alerts", null, null);
            SQLiteStatement st = db.compileStatement("INSERT OR REPLACE INTO alerts " +
                    "(id, title, body, type, severity, payload_json, dedupe_key, created_ms, is_read, saved_at) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?)");
            for (AlertItem a : list) {
                if (a.getId() == null) continue;
                st.clearBindings();
                st.bindString(1, a.getId());
                bindText(st, 2, a.getTitle());
                bindText(st, 3, a.getBody());
                bindText(st, 4, a.getType());
                bindText(st, 5, a.getSeverity());
                bindText(st, 6, a.getPayloadJson());
                bindText(st, 7, a.getDedupeKey());
                st.bindLong(8, a.getCreatedAt().toDate().getTime());
                st.bindLong(9, a.getRead() ? 1 : 0);
                st.bindLong(10, now);
                st.executeInsert();
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public List<AlertItem> getAlerts() {
        List<AlertItem> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, title, body, type, severity, payload_json, dedupe_key, created_ms, is_read " +
                        "FROM alerts ORDER BY created_ms DESC", null)) {
            while (c.moveToNext()) {
                AlertItem a = new AlertItem();
                a.setId(c.getString(0));
                a.setTitle(c.getString(1));
                a.setBody(c.getString(2));
                a.setType(c.getString(3));
                a.setSeverity(c.getString(4));
                a.setPayloadJson(c.getString(5));
                a.setDedupeKey(c.getString(6));
                a.setCreatedAt(new Timestamp(new Date(c.getLong(7))));
                a.setRead(c.getInt(8) == 1);
                out.add(a);
            }
        }
        return out;
    }

    public long alertCount() {
        return DatabaseUtils.queryNumEntries(getReadableDatabase(), "alerts");
    }

    // ---------------- budget (single row) ----------------

    public void saveBudget(BudgetConfig b) {
        JSONObject cats = new JSONObject();
        try {
            if (b.getCategoryBudgets() != null) {
                for (Map.Entry<String, Double> en : b.getCategoryBudgets().entrySet()) cats.put(en.getKey(), en.getValue());
            }
        } catch (Exception ignored) { /* keys are plain category names */ }
        SQLiteStatement st = getWritableDatabase().compileStatement(
                "INSERT OR REPLACE INTO budget (id, monthly, category_json) VALUES (1, ?, ?)");
        st.bindDouble(1, b.getMonthlyBudget());
        st.bindString(2, cats.toString());
        st.executeInsert();
    }

    /** Null when no budget has been stored yet. */
    public BudgetConfig getBudget() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT monthly, category_json FROM budget WHERE id = 1", null)) {
            if (!c.moveToFirst()) return null;
            BudgetConfig b = new BudgetConfig();
            b.setMonthlyBudget(c.getDouble(0));
            Map<String, Double> cats = new HashMap<>();
            try {
                JSONObject o = new JSONObject(c.isNull(1) ? "{}" : c.getString(1));
                for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                    String k = it.next();
                    cats.put(k, o.getDouble(k));
                }
            } catch (Exception ignored) { /* corrupt row: fall back to no category budgets */ }
            b.setCategoryBudgets(cats);
            return b;
        }
    }

    /** When the cache was last refreshed (ms since epoch), or 0 if it is empty. */
    public long lastSavedAt() {
        return DatabaseUtils.longForQuery(getReadableDatabase(), "SELECT IFNULL(MAX(saved_at), 0) FROM expenses", null);
    }

    private static void bindText(SQLiteStatement st, int index, String value) {
        if (value == null) st.bindNull(index);
        else st.bindString(index, value);
    }
}
