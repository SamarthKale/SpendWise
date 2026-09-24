package com.mj.spendwise.backend;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * The "who has logged in on this device" registry: one SQLite file (spendwise_app.db) with one row per login
 * profile. Each profile's own expenses/alerts/budget live in that profile's separate database file
 * ({@link LocalDatabase}); this registry only remembers who exists, which file is theirs, and when they last logged in.
 */
public class LocalProfiles extends SQLiteOpenHelper {
    public static final String NAME = "spendwise_app.db";
    private static final int VERSION = 1;
    private static LocalProfiles instance;

    /** One row of the registry. */
    public static class Profile {
        public String profileKey;
        public String uid;
        public String email;
        public boolean guest;
        public String dbFile;
        public long createdAt;
        public long lastLoginAt;
        public int loginCount;
    }

    public static synchronized LocalProfiles get(Context context) {
        if (instance == null) instance = new LocalProfiles(context.getApplicationContext());
        return instance;
    }

    private LocalProfiles(Context context) {
        super(context, NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE profiles (" +
                "profile_key TEXT PRIMARY KEY, uid TEXT, email TEXT, is_guest INTEGER NOT NULL, db_file TEXT NOT NULL, " +
                "created_at INTEGER NOT NULL, last_login_at INTEGER NOT NULL, login_count INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS profiles");
        onCreate(db);
    }

    /** Called on every successful login. Creates the profile row the first time, then counts logins. */
    public Profile recordLogin(String uid, String email, boolean guest) {
        String key = ProfileKeys.keyFor(uid, guest);
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        db.beginTransaction();
        try {
            Profile existing = get(key);
            ContentValues v = new ContentValues();
            v.put("uid", uid);
            v.put("email", email);
            v.put("is_guest", guest ? 1 : 0);
            v.put("last_login_at", now);
            if (existing == null) {
                v.put("profile_key", key);
                v.put("db_file", ProfileKeys.dbFileName(key));
                v.put("created_at", now);
                v.put("login_count", 1);
                db.insert("profiles", null, v);
            } else {
                v.put("login_count", existing.loginCount + 1);
                db.update("profiles", v, "profile_key = ?", new String[]{key});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return get(key);
    }

    /** Null when that profile has never logged in on this device. */
    public Profile get(String profileKey) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT profile_key, uid, email, is_guest, db_file, created_at, last_login_at, login_count " +
                        "FROM profiles WHERE profile_key = ?", new String[]{profileKey})) {
            return c.moveToFirst() ? read(c) : null;
        }
    }

    public List<Profile> list() {
        List<Profile> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT profile_key, uid, email, is_guest, db_file, created_at, last_login_at, login_count " +
                        "FROM profiles ORDER BY last_login_at DESC", null)) {
            while (c.moveToNext()) out.add(read(c));
        }
        return out;
    }

    public long count() {
        return DatabaseUtils.queryNumEntries(getReadableDatabase(), "profiles");
    }

    private static Profile read(Cursor c) {
        Profile p = new Profile();
        p.profileKey = c.getString(0);
        p.uid = c.getString(1);
        p.email = c.getString(2);
        p.guest = c.getInt(3) == 1;
        p.dbFile = c.getString(4);
        p.createdAt = c.getLong(5);
        p.lastLoginAt = c.getLong(6);
        p.loginCount = c.getInt(7);
        return p;
    }
}
