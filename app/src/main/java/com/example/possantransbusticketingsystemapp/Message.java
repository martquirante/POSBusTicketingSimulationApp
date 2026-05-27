package com.example.possantransbusticketingsystemapp;

/**
 * This is a model class that represents a single chat message.
 * It now supports three types: TEXT, IMAGE, and VIDEO.
 */
public class Message {

    private String type;
    private String text;
    private String imageUrl;
    private String videoUrl;
    private String sender;
    private long timestamp;

    // Empty constructor required for Firebase
    public Message() {
    }

    // Constructor for Text messages
    public Message(String text, String sender) {
        this.type = "TEXT";
        this.text = text;
        this.sender = sender;
        this.timestamp = System.currentTimeMillis();
    }

    // Constructor for Image or Video messages
    public Message(String fileUrl, String sender, String type) {
        this.type = type; // "IMAGE" or "VIDEO"
        if ("IMAGE".equals(type)) {
            this.imageUrl = fileUrl;
        } else if ("VIDEO".equals(type)) {
            this.videoUrl = fileUrl;
        }
        this.sender = sender;
        this.timestamp = System.currentTimeMillis();
    }

    // Getters
    public String getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public String getSender() {
        return sender;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
