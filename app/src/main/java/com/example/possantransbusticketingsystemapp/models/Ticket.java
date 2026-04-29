package com.example.possantransbusticketingsystemapp.models;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
public class Ticket {

    protected String busNumber;
    protected String driverName;
    protected String conductorName;
    protected String origin;
    protected String destination;
    protected String passengerType;
    protected double price;
    protected String paymentMethod;
    protected long timestamp;

    // Constructor
    public Ticket(String busNumber, String driverName, String conductorName,
                  String origin, String destination, String passengerType,
                  double price, String paymentMethod) {
        this.busNumber = busNumber;
        this.driverName = driverName;
        this.conductorName = conductorName;
        this.origin = origin;
        this.destination = destination;
        this.passengerType = passengerType;
        this.price = price;
        this.paymentMethod = paymentMethod;
        this.timestamp = System.currentTimeMillis();
    }

    // Getters
    public String getBusNumber() { return busNumber; }
    public String getDriverName() { return driverName; }
    public String getConductorName() { return conductorName; }
    public String getOrigin() { return origin; }
    public String getDestination() { return destination; }
    public String getPassengerType() { return passengerType; }
    public double getPrice() { return price; }
    public String getPaymentMethod() { return paymentMethod;
    }

    // Helper para sa filename (Format: 3-09pm_12-07-2025)
    public String getFormattedDateForFile() {
        SimpleDateFormat sdf = new SimpleDateFormat("h-mma_MM-dd-yyyy", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    // Helper para sa loob ng receipt text
    public String getFormattedDateForPrint() {
        SimpleDateFormat sdf = new SimpleDateFormat("h:mm a | MM/dd/yyyy", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }
}
