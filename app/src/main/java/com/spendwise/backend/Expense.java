package com.spendwise.backend;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;

/**
 * Firestore POJO for users/{uid}/expenses/{id}.
 * Firestore needs a public no-arg constructor and public getters/setters.
 */
public class Expense {
    private String id;            // document id, filled from the snapshot (not stored in the doc)
    private String merchant = "";
    private String category = "";
    private double amount;        // INR
    private String notes = "";
    private String locationName = "";
    private Double latitude;      // nullable: only set if GPS was attached
    private Double longitude;
    private Timestamp timestamp = Timestamp.now();
    private String source = "manual";   // "manual" or "receipt_scan"
    private boolean pending;      // true while the write is not yet acknowledged by the server

    public Expense() { }

    /** Copy so the UI can edit without mutating the object held by the live list. */
    public Expense copy() {
        Expense e = new Expense();
        e.id = id;
        e.merchant = merchant;
        e.category = category;
        e.amount = amount;
        e.notes = notes;
        e.locationName = locationName;
        e.latitude = latitude;
        e.longitude = longitude;
        e.timestamp = timestamp;
        e.source = source;
        return e;
    }

    @Exclude public String getId() { return id; }
    @Exclude public void setId(String id) { this.id = id; }

    public String getMerchant() { return merchant; }
    public void setMerchant(String merchant) { this.merchant = merchant; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getLocationName() { return locationName; }
    public void setLocationName(String locationName) { this.locationName = locationName; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Timestamp getTimestamp() { return timestamp; }
    public void setTimestamp(Timestamp timestamp) { this.timestamp = timestamp; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    @Exclude public boolean getPending() { return pending; }
    @Exclude public void setPending(boolean pending) { this.pending = pending; }
}
