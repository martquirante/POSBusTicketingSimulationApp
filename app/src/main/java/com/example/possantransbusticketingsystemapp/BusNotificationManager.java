package com.example.possantransbusticketingsystemapp;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.view.View;
import android.widget.RemoteViews;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.util.Locale;

public class BusNotificationManager {

    private static final String CHANNEL_LIVE_ID = "live_status_channel";
    private static final String CHANNEL_ALERT_ID = "alert_channel_v2";
    private static final int NOTIF_ID_LIVE = 100;

    private Context context;

    public BusNotificationManager(Context context) {
        this.context = context;
        createChannels();
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            Uri soundUri = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + context.getPackageName() + "/" + R.raw.custom_notif);

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build();

            NotificationChannel liveChannel = new NotificationChannel(CHANNEL_LIVE_ID, "Bus Status (Silent)", NotificationManager.IMPORTANCE_LOW);
            liveChannel.setSound(null, null);
            liveChannel.setShowBadge(false);

            NotificationChannel alertChannel = new NotificationChannel(CHANNEL_ALERT_ID, "Ticket Alerts", NotificationManager.IMPORTANCE_HIGH);
            alertChannel.setSound(soundUri, audioAttributes);

            if (manager != null) {
                manager.createNotificationChannel(liveChannel);
                manager.createNotificationChannel(alertChannel);
            }
        }
    }

    // =========================================================================
    //   1. LIVE STATUS UPDATE (FIXED: USE INDIVIDUAL COUNTS FOR ACCURACY)
    // =========================================================================

    public void updateLiveStatus(String loopName, int rawReg, int rawStu, int rawSen, double rawTotalCash, double rawTotalGcash, String lastTicketID) {

        // 1. BASAHIN ANG OFFSETS (Zero Point from Conductor Menu Reset)
        SharedPreferences offsets = context.getSharedPreferences("BusDataOffsets", Context.MODE_PRIVATE);
        int offReg = offsets.getInt("offset_reg", 0);
        int offStu = offsets.getInt("offset_stu", 0);
        int offSen = offsets.getInt("offset_sen", 0);
        double offCash = offsets.getFloat("offset_cash", 0);
        double offGcash = offsets.getFloat("offset_gcash", 0);

        // 2. APPLY OFFSET PER CATEGORY (Cloud - Offset)
        int currentReg = Math.max(0, rawReg - offReg);
        int currentStu = Math.max(0, rawStu - offStu);
        int currentSen = Math.max(0, rawSen - offSen);

        // 3. COMPUTE TOTAL PAX (Sum of corrected values)
        // Ito na ang magiging parehas sa Conductor's Menu
        int displayPax = currentReg + currentStu + currentSen;

        double displayCash = Math.max(0.0, rawTotalCash - offCash);
        double displayGcash = Math.max(0.0, rawTotalGcash - offGcash);

        // 4. I-SAVE SA LOCAL STATS (Para consistent sa ibang screens)
        SharedPreferences stats = context.getSharedPreferences("BusStats", Context.MODE_PRIVATE);
        stats.edit()
                .putInt("totalPax", displayPax)
                .putFloat("cashAmount", (float) displayCash)
                .putFloat("gcashAmount", (float) displayGcash)
                .putString("lastTicketID", lastTicketID)
                .putString("lastLoop", loopName)
                .apply();

        // 5. UPDATE NOTIFICATION UI
        String mainTitleLoop = "FVR to ST.CRUZ";
        boolean isReverseLoop = false;
        if (loopName != null && loopName.toUpperCase().contains("ST.CRUZ") && loopName.toUpperCase().startsWith("ST")) {
            mainTitleLoop = "ST.CRUZ to FVR";
            isReverseLoop = true;
        }

        RemoteViews customView = new RemoteViews(context.getPackageName(), R.layout.notification_bus_status);

        customView.setTextViewText(R.id.notifLoopName, mainTitleLoop);
        customView.setTextViewText(R.id.notifLastRoute, "Current: " + loopName);
        customView.setViewVisibility(R.id.notifLastRoute, View.VISIBLE);
        customView.setTextViewText(R.id.notifLastTicket, "#" + lastTicketID);

        // --- CORRECTED TOTAL PAX DISPLAY ---
        customView.setTextViewText(R.id.notifTotalPax, String.valueOf(displayPax));

        // --- FINANCIALS ---
        customView.setTextViewText(R.id.notifCashCount, "P" + String.format(Locale.US, "%.2f", displayCash));
        customView.setTextViewText(R.id.notifGcashCount, "P" + String.format(Locale.US, "%.2f", displayGcash));

        Intent notificationIntent = new Intent(context, RouteListActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        SharedPreferences mainPrefs = context.getSharedPreferences("SantransPrefs", Context.MODE_PRIVATE);
        notificationIntent.putExtra("BUS_NO", mainPrefs.getString("busNumber", "---"));
        notificationIntent.putExtra("DRIVER", mainPrefs.getString("driverName", "---"));
        notificationIntent.putExtra("CONDUCTOR", mainPrefs.getString("conductorName", "---"));
        notificationIntent.putExtra("IS_REVERSE", isReverseLoop);

        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Bitmap largeIcon = null;
        try {
            largeIcon = BitmapFactory.decodeResource(context.getResources(), R.drawable.bus_logo);
        } catch (Exception e) { e.printStackTrace(); }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_LIVE_ID)
                .setSmallIcon(android.R.drawable.ic_menu_directions)
                .setColor(ContextCompat.getColor(context, R.color.brand_primary))
                .setLargeIcon(largeIcon)
                .setCustomContentView(customView)
                .setCustomBigContentView(customView)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_MAX);

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_LIVE, builder.build());
        } catch (SecurityException e) { e.printStackTrace(); }
    }

    // =========================================================================
    //   2. TICKET SAVED ALERT
    // =========================================================================
    public void showSaveAlert(boolean isSuccess, String message, File ticketFile) {
        sendGenericFileAlert(isSuccess, "Ticket Issued", message, ticketFile, "application/pdf");
    }

    // =========================================================================
    //   3. EXCEL EXPORT ALERT
    // =========================================================================
    public void showExcelGeneratedAlert(File excelFile) {
        String msg = "Excel Report generated successfully.";
        sendGenericFileAlert(true, "Excel Exported", msg, excelFile, "application/vnd.ms-excel");
    }

    // =========================================================================
    //   4. CONDUCTOR REPORT ALERT
    // =========================================================================
    public void showReportGeneratedAlert(File reportFile) {
        String msg = "Conductor's Report is ready to print.";
        sendGenericFileAlert(true, "Report Generated", msg, reportFile, "application/pdf");
    }

    // =========================================================================
    //   HELPER (CLICKABLE NOTIFICATION FIX)
    // =========================================================================
    private void sendGenericFileAlert(boolean isSuccess, String title, String message, File file, String mimeType) {
        int icon = isSuccess ? android.R.drawable.stat_sys_upload_done : android.R.drawable.stat_notify_error;
        Uri soundUri = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + context.getPackageName() + "/" + R.raw.custom_notif);

        if (!isSuccess) title = "Error";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ALERT_ID)
                .setSmallIcon(icon)
                .setColor(ContextCompat.getColor(context, R.color.brand_primary))
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSound(soundUri)
                .setOngoing(false)
                .setAutoCancel(true);

        if (isSuccess && file != null && file.exists()) {
            try {
                Uri fileUri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", file);
                Intent openIntent = new Intent(Intent.ACTION_VIEW);
                openIntent.setDataAndType(fileUri, mimeType);
                openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NO_HISTORY);

                PendingIntent pendingIntent = PendingIntent.getActivity(
                        context,
                        (int) System.currentTimeMillis(),
                        openIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );
                builder.setContentIntent(pendingIntent);
            } catch (Exception e) { e.printStackTrace(); }
        }

        try {
            NotificationManagerCompat.from(context).notify((int) System.currentTimeMillis(), builder.build());
        } catch (SecurityException e) { e.printStackTrace(); }
    }

    // =========================================================================
    //   RESET STATS (FORCES ZERO DISPLAY ON NEW TRIP)
    // =========================================================================
    public void resetStats() {
        context.getSharedPreferences("BusStats", Context.MODE_PRIVATE).edit().clear().apply();
        context.getSharedPreferences("TicketCounter", Context.MODE_PRIVATE)
                .edit().putInt("last_ticket_count", 0).apply();

        // Calculate offsets to force a "0" display
        SharedPreferences offsets = context.getSharedPreferences("BusDataOffsets", Context.MODE_PRIVATE);
        int offReg = offsets.getInt("offset_reg", 0);
        int offStu = offsets.getInt("offset_stu", 0);
        int offSen = offsets.getInt("offset_sen", 0);
        double offCash = offsets.getFloat("offset_cash", 0);
        double offGcash = offsets.getFloat("offset_gcash", 0);

        // Trick: Pass OFFSETS as RAW values -> (Offset - Offset = 0)
        updateLiveStatus("WAITING FOR PASSENGERS...", offReg, offStu, offSen, offCash, offGcash, "--");
    }
}