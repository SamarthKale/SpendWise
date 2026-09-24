package com.mj.spendwise.backend;

/**
 * Decides WHICH local SQLite file belongs to a login. Pure Java (no Android), so it is unit-tested.
 *  - every guest shares one file, "spendwise_guest.db" (pre-filled with demo data on first run)
 *  - every email account gets its own file, "spendwise_u_&lt;uid&gt;.db" (starts empty)
 */
public final class ProfileKeys {
    public static final String GUEST = "guest";

    private ProfileKeys() { }

    /** The key that names a login's database: "guest" for guests, the Firebase uid for real accounts. */
    public static String keyFor(String uid, boolean isGuest) {
        return isGuest ? GUEST : uid;
    }

    /** File name inside the app's databases folder. Only letters, digits and underscores survive. */
    public static String dbFileName(String profileKey) {
        String safe = profileKey.replaceAll("[^A-Za-z0-9_]", "_");
        return GUEST.equals(profileKey) ? "spendwise_guest.db" : "spendwise_u_" + safe + ".db";
    }
}
