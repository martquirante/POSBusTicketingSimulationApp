package com.buscorp.employee.core.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.UUID;

@Entity(tableName = "tickets")
public class TicketEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    private String id;

    @ColumnInfo(name = "origin")
    private String origin;

    @ColumnInfo(name = "destination")
    private String destination;

    @ColumnInfo(name = "fare")
    private double fare;

    @ColumnInfo(name = "passenger_type")
    private String passengerType;

    @ColumnInfo(name = "timestamp")
    private long timestamp;

    @ColumnInfo(name = "is_synced")
    private boolean isSynced;

    public TicketEntity(String origin, String destination, double fare, String passengerType, long timestamp, boolean isSynced) {
        this.id = UUID.randomUUID().toString();
        this.origin = origin;
        this.destination = destination;
        this.fare = fare;
        this.passengerType = passengerType;
        this.timestamp = timestamp;
        this.isSynced = isSynced;
    }

    // --- Getters & Setters ---

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

    public boolean isSynced() { return isSynced; }
    public void setSynced(boolean synced) { isSynced = synced; }
}
