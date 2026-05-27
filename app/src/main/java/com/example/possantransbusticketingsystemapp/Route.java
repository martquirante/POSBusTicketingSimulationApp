package com.example.possantransbusticketingsystemapp;

public class Route {
    public String origin;
    public String destination;
    public double price;

    // IMPORTANT: Empty constructor for Firebase
    public Route() {
    }

    public Route(String origin, String destination, double price) {
        this.origin = origin;
        this.destination = destination;
        this.price = price;
    }
}