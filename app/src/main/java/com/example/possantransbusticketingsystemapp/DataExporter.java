package com.example.possantransbusticketingsystemapp;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Environment;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DataExporter {

    // Ito yung tinatawag mo sa TicketHistoryActivity
    public static void generateReport(Context context, List<TicketTransaction> ticketList, String sessionID) {

        // 1. SETUP PDF (A4 Size)
        PdfDocument document = new PdfDocument();
        int pageHeight = 1120;
        int pageWidth = 792;
        int margin = 50;

        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();

        // 2. SETUP PAINTS (Ang Pangsulat)
        Paint titlePaint = new Paint();
        titlePaint.setColor(Color.BLACK);
        titlePaint.setTextSize(24);
        titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        titlePaint.setTextAlign(Paint.Align.CENTER);

        Paint headerPaint = new Paint();
        headerPaint.setColor(Color.BLACK);
        headerPaint.setTextSize(14);
        headerPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        Paint textPaint = new Paint();
        textPaint.setColor(Color.DKGRAY);
        textPaint.setTextSize(12);

        Paint linePaint = new Paint();
        linePaint.setColor(Color.GRAY);
        linePaint.setStrokeWidth(1);

        // 3. HEADER (Logo + Title)
        int y = margin + 20;

        // Draw Logo (Kung meron sa drawable)
        Bitmap logo = BitmapFactory.decodeResource(context.getResources(), R.drawable.bus_logo);
        if (logo != null) {
            Bitmap scaledLogo = Bitmap.createScaledBitmap(logo, 80, 80, false);
            canvas.drawBitmap(scaledLogo, margin, margin, null);
        }

        // Title
        canvas.drawText("POS Bus Ticketing Simulation.", pageWidth / 2, y, titlePaint);
        y += 30;

        Paint subTitlePaint = new Paint();
        subTitlePaint.setTextSize(14);
        subTitlePaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("Daily Trip Collection Report", pageWidth / 2, y, subTitlePaint);
        y += 60;

        // 4. TRIP DETAILS
        String dateNow = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US).format(new Date());
        canvas.drawText("Trip Session: " + sessionID, margin, y, headerPaint);
        canvas.drawText("Generated: " + dateNow, pageWidth - margin - 220, y, textPaint);
        y += 30;

        canvas.drawLine(margin, y, pageWidth - margin, y, linePaint); // Line Separator
        y += 20;

        // 5. TABLE HEADERS
        int col1 = margin;       // Ticket ID
        int col2 = margin + 150; // Route
        int col3 = margin + 400; // Type
        int col4 = pageWidth - margin - 100; // Amount

        canvas.drawText("TICKET ID", col1, y, headerPaint);
        canvas.drawText("ROUTE", col2, y, headerPaint);
        canvas.drawText("TYPE / PAX", col3, y, headerPaint);
        canvas.drawText("AMOUNT", col4, y, headerPaint);
        y += 15;
        canvas.drawLine(margin, y, pageWidth - margin, y, linePaint);
        y += 20;

        // 6. PRINT TICKETS (LOOP)
        double totalCash = 0;
        double totalGcash = 0;
        int totalPax = 0;

        for (TicketTransaction t : ticketList) {
            // Check kung puno na ang papel, gawa bago page
            if (y > pageHeight - 100) {
                document.finishPage(page);
                page = document.startPage(pageInfo);
                canvas = page.getCanvas();
                y = margin + 20;
            }

            canvas.drawText(t.getTicketID(), col1, y, textPaint);

            // Shorten Route Text if too long
            String route = t.getOrigin() + "-" + t.getDestination();
            if (route.length() > 30) route = route.substring(0, 27) + "...";
            canvas.drawText(route, col2, y, textPaint);

            canvas.drawText(t.getPassengerType() + " (" + t.getPassengerCount() + ")", col3, y, textPaint);
            canvas.drawText("P " + String.format("%.2f", t.getTotalAmount()), col4, y, textPaint);

            // Compute Totals
            if (t.getPaymentMethod().equalsIgnoreCase("GCASH")) {
                totalGcash += t.getTotalAmount();
            } else {
                totalCash += t.getTotalAmount();
            }
            totalPax += t.getPassengerCount();

            y += 20; // Next Row
        }

        // 7. FOOTER SUMMARY
        y += 20;
        canvas.drawLine(margin, y, pageWidth - margin, y, linePaint);
        y += 30;

        Paint totalPaint = new Paint();
        totalPaint.setTextSize(14);
        totalPaint.setTypeface(Typeface.DEFAULT_BOLD);
        totalPaint.setTextAlign(Paint.Align.RIGHT);

        int rightAlign = pageWidth - margin;

        canvas.drawText("Total Passengers: " + totalPax, rightAlign, y, totalPaint);
        y += 25;
        canvas.drawText("Total Cash: P " + String.format("%.2f", totalCash), rightAlign, y, totalPaint);
        y += 25;
        canvas.drawText("Total GCash: P " + String.format("%.2f", totalGcash), rightAlign, y, totalPaint);
        y += 35;

        Paint grandTotalPaint = new Paint();
        grandTotalPaint.setTextSize(18);
        grandTotalPaint.setColor(Color.parseColor("#0066CC"));
        grandTotalPaint.setTypeface(Typeface.DEFAULT_BOLD);
        grandTotalPaint.setTextAlign(Paint.Align.RIGHT);

        double grandTotal = totalCash + totalGcash;
        canvas.drawText("GRAND TOTAL: P " + String.format("%.2f", grandTotal), rightAlign, y, grandTotalPaint);

        // 8. SIGNATURE
        y += 80;
        canvas.drawLine(margin, y, margin + 200, y, linePaint);
        canvas.drawText("Conductor's Signature", margin, y + 20, textPaint);

        document.finishPage(page);

        // 9. SAVE & NOTIFY
        saveAndOpenPdf(context, document, sessionID);
    }

    private static void saveAndOpenPdf(Context context, PdfDocument document, String sessionID) {
        try {
            File docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            File appDir = new File(docsDir, "SANTRANS_POS_FILES/REPORTS");
            if (!appDir.exists()) appDir.mkdirs();

            String fileName = "REPORT_" + sessionID + ".pdf";
            File file = new File(appDir, fileName);

            FileOutputStream fos = new FileOutputStream(file);
            document.writeTo(fos);
            document.close();
            fos.close();

            // Sound Effect (Optional)
            try {
                MediaPlayer mp = MediaPlayer.create(context, R.raw.custom_notif);
                if(mp != null) mp.start();
            } catch (Exception e){}

            Toast.makeText(context, "✅ Report Saved: " + fileName, Toast.LENGTH_LONG).show();

            // Show Notification
            showNotification(context, file);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "Error saving PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private static void showNotification(Context context, File file) {
        String channelId = "report_channel";
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Reports", NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(channel);
        }

        // Open File Intent
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(FileProvider.getUriForFile(context, context.getPackageName() + ".provider", file), "application/pdf");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        PendingIntent pi = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Report Generated")
                .setContentText("Tap to view: " + file.getName())
                .setContentIntent(pi)
                .setAutoCancel(true);

        nm.notify(300, builder.build());
    }
}