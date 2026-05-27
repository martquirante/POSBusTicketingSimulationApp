package com.buscorp.employee.core.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "sync_conflicts")
public class SyncConflictEntity {

    @PrimaryKey
    @NonNull
    private String id;
    private String origin;
    private String destination;
    private double fare;
    private String passengerType;
    private long timestamp;
    private String conflictReason;

    public SyncConflictEntity(@NonNull String id, String origin, String destination, double fare, String passengerType, long timestamp, String conflictReason) {
        this.id = id;
        this.origin = origin;
        this.destination = destination;
        this.fare = fare;
        this.passengerType = passengerType;
        this.timestamp = timestamp;
        this.conflictReason = conflictReason;
    }

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public double getFare() { return fare; }
    public void setFare(double fare) { this.fare = fare; }
    public String getPassengerType() { return passengerType; }
    public void setPassengerType(String passengerType) { this.passengerType = passengerType; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public String getConflictReason() { return conflictReason; }
    public void setConflictReason(String conflictReason) { this.conflictReason = conflictReason; }
}
