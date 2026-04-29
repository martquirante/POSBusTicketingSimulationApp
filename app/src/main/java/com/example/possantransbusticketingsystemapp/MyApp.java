package com.example.possantransbusticketingsystemapp;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;

import com.google.firebase.database.FirebaseDatabase;

public class MyApp extends Application {

    // Channel IDs (Dapat tugma ito sa BusNotificationManager)
    public static final String CHANNEL_LIVE_ID = "live_status_channel";
    public static final String CHANNEL_ALERT_ID = "alert_channel_v2";
    public static final String CHANNEL_PRINT_ID = "print_notifications"; // Para sa Reports

    @Override
    public void onCreate() {
        super.onCreate();

        // 1. FIREBASE OFFLINE PERSISTENCE (Code mo)
        // Ito ang nagpapa-gana ng save kahit walang net.
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);

        // 2. SETUP NOTIFICATION CHANNELS (Fix para sa Notif)
        // Kailangan ito para gumana ang notifications sa Android 8.0 pataas
        createNotificationChannels();
    }

    private void createNotificationChannels() {
        // Check kung Android 8.0 (Oreo) pataas
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            // A. LIVE STATUS CHANNEL (Silent, Low Priority)
            // Ito yung persistent bar sa taas na "FVR to ST.CRUZ"
            NotificationChannel liveChannel = new NotificationChannel(
                    CHANNEL_LIVE_ID,
                    "Bus Live Status",
                    NotificationManager.IMPORTANCE_LOW
            );
            liveChannel.setDescription("Shows real-time passenger count");
            liveChannel.setShowBadge(false);

            // B. TICKET ALERTS CHANNEL (May Tunog, High Priority)
            // Ito yung "Ticket Saved" pop-up
            NotificationChannel alertChannel = new NotificationChannel(
                    CHANNEL_ALERT_ID,
                    "Ticket Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            alertChannel.setDescription("Notifications for ticket issuance");

            // C. PRINT REPORTS CHANNEL (Para sa Conductor Report)
            NotificationChannel printChannel = new NotificationChannel(
                    CHANNEL_PRINT_ID,
                    "Print Reports",
                    NotificationManager.IMPORTANCE_HIGH
            );
            printChannel.setDescription("Notifications when PDF reports are generated");

            // --- CUSTOM SOUND SETUP (Optional) ---
            // Kung may custom_notif.wav ka sa res/raw, gagamitin niya yun.
            // Kung wala, default sound ang gagamitin.
            Uri soundUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.custom_notif);
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build();

            alertChannel.setSound(soundUri, audioAttributes);
            printChannel.setSound(soundUri, audioAttributes);

            // REGISTER CHANNELS SA SYSTEM
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(liveChannel);
                manager.createNotificationChannel(alertChannel);
                manager.createNotificationChannel(printChannel);
            }
        }
    }
}