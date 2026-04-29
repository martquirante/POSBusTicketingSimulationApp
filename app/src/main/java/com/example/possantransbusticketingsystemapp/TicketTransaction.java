package com.example.possantransbusticketingsystemapp;

public class TicketTransaction {
    private String busNo;
    private String driver;
    private String conductor;
    private String origin;
    private String destination;
    private String passengerType;
    private int passengerCount;
    private double baseFare;
    private double discountAmount;
    private double totalAmount;
    private double cashReceived;
    private double changeAmount;
    private String paymentMethod;
    private String timestamp;
    private String deviceModel;
    private String ticketID;
    private double latitude;
    private double longitude;
    private String locationName;
    private String status;

    // 1. Empty Constructor (Required ni Firebase)
    public TicketTransaction() { }

    // 2. FULL CONSTRUCTOR (Ito ang hinahanap ng GcashQrActivity mo)
    public TicketTransaction(String busNo, String driver, String conductor, String origin, String destination,
                             String passengerType, int passengerCount, double baseFare, double discountAmount,
                             double totalAmount, String paymentMethod, String timestamp, String deviceModel,
                             String ticketID, double latitude, double longitude, String locationName) {
        this.busNo = busNo;
        this.driver = driver;
        this.conductor = conductor;
        this.origin = origin;
        this.destination = destination;
        this.passengerType = passengerType;
        this.passengerCount = passengerCount;
        this.baseFare = baseFare;
        this.discountAmount = discountAmount;
        this.totalAmount = totalAmount;
        this.paymentMethod = paymentMethod;
        this.timestamp = timestamp;
        this.deviceModel = deviceModel;
        this.ticketID = ticketID;
        this.latitude = latitude;
        this.longitude = longitude;
        this.locationName = locationName;

        // Auto-set fields for GCash
        this.cashReceived = totalAmount; // Exact payment
        this.changeAmount = 0.0;
        this.status = "PAID";
    }

    // Getters
    public String getTicketID() { return ticketID; }
    public String getBusNo() { return busNo; }
    public String getDriver() { return driver; }
    public String getConductor() { return conductor; }
    public String getOrigin() { return origin; }
    public String getDestination() { return destination; }
    public String getPassengerType() { return passengerType; }
    public int getPassengerCount() { return passengerCount; }
    public double getBaseFare() { return baseFare; }
    public double getDiscountAmount() { return discountAmount; }
    public double getTotalAmount() { return totalAmount; }
    public double getCashReceived() { return cashReceived; }
    public double getChangeAmount() { return changeAmount; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getTimestamp() { return timestamp; }
    public String getDeviceModel() { return deviceModel; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getLocationName() { return locationName; }
    public String getStatus() { return status; }
}