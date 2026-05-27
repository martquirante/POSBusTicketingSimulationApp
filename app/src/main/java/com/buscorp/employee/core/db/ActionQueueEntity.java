package com.buscorp.employee.core.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "action_queue")
public class ActionQueueEntity {
    @PrimaryKey
    @NonNull
    public String localId;
    public String actionType;
    public String payloadJson;
    public long createdAt;
    public int retryCount;

    public ActionQueueEntity(@NonNull String localId, String actionType, String payloadJson, long createdAt, int retryCount) {
        this.localId = localId;
        this.actionType = actionType;
        this.payloadJson = payloadJson;
        this.createdAt = createdAt;
        this.retryCount = retryCount;
    }
}
