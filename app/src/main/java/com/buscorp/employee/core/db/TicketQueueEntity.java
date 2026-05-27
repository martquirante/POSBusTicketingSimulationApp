package com.buscorp.employee.core.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "ticket_queue")
public class TicketQueueEntity {
    @PrimaryKey
    @NonNull
    public String localId;
    public String payloadJson;
    public String status;
    public long createdAt;
    public int retryCount;

    public TicketQueueEntity(@NonNull String localId, String payloadJson, String status, long createdAt, int retryCount) {
        this.localId = localId;
        this.payloadJson = payloadJson;
        this.status = status;
        this.createdAt = createdAt;
        this.retryCount = retryCount;
    }
}
