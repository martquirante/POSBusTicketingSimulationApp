package com.buscorp.employee.data.local;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(tableName = "tickets")
public class TicketEntity {

    @PrimaryKey
    @NonNull
    public String ticket_id;

    public String route;
    public String origin;
    public String destination;
    public double fare;
    public String passenger_type;
    public long timestamp;
    public boolean is_synced;

    // Optional additional fields that might be useful
    public String conductor_id;
    public String bus_id;
    public String payment_method; // "CASH" or "QR"

    public TicketEntity() {
        this.ticket_id = "";
    }
}
