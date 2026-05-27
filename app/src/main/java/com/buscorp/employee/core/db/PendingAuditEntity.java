package com.buscorp.employee.core.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "pending_audit")
public class PendingAuditEntity {
    @PrimaryKey
    @NonNull
    public String localId;
    public String tableName;
    public String payloadJson;
    public String previousHash;
    public String hash;
    public long createdAt;

    public PendingAuditEntity(@NonNull String localId, String tableName, String payloadJson, String previousHash, String hash, long createdAt) {
        this.localId = localId;
        this.tableName = tableName;
        this.payloadJson = payloadJson;
        this.previousHash = previousHash;
        this.hash = hash;
        this.createdAt = createdAt;
    }
}
