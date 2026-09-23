package com.mj.spendwise.backend;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;

/** Firestore POJO for users/{uid}/alerts/{id}. Used from Phase 8. */
public class AlertItem {
    private String id;
    private String title = "";
    private String body = "";
    private String type = "";        // e.g. budget_80, anomaly
    private String severity = "info"; // info | warning | critical
    private String payloadJson = "{}";
    private String dedupeKey;        // rule + month, so the same alert is not created twice
    private Timestamp createdAt = Timestamp.now();
    private boolean read;

    public AlertItem() { }

    @Exclude public String getId() { return id; }
    @Exclude public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }

    public String getDedupeKey() { return dedupeKey; }
    public void setDedupeKey(String dedupeKey) { this.dedupeKey = dedupeKey; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public boolean getRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
}
