package com.example.possantransbusticketingsystemapp;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ConductorMenuActivity extends BaseActivity {

    private static final int RC_ASSISTANCE_PHOTO_PICKER = 3;
    private static final int PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE_ASSISTANCE = 2;

    // --- UI ELEMENTS ---
    private TextView tvBus, tvDriver, tvConductor, tvCurrentLoop;
    private TextView tvRegularPax, tvStudentPax, tvSeniorPax, tvTotalPax;
    private TextView tvTotalCollected, tvTotalGcash, tvTotalCash;
    private Button btnNewTrip, btnPrint, btnViewData, btnBack;
    private ImageButton btnSettings, btnHelp, btnChat;

    // --- FIREBASE ---
    private DatabaseReference deviceRef;
    private DatabaseReference assistanceRef;
    private FirebaseStorage firebaseStorage;
    private StorageReference assistancePhotosStorageReference;
    private ValueEventListener syncListener;
    private String currentSessionId;
    private String deviceSerial;

    private boolean isEmergencyActive = false;
    private String pendingAssistanceIssue = "";

    // --- SECURITY PASSCODE (Default) ---
    private static final String ADMIN_PASSCODE = "1234";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conductor_menu);

        // 1. FIREBASE SETUP
        deviceSerial = android.os.Build.MODEL.replaceAll("[^a-zA-Z0-9]", "_").toUpperCase();
        deviceRef = FirebaseDatabase.getInstance().getReference("POS_Devices").child(deviceSerial);
        assistanceRef = FirebaseDatabase.getInstance().getReference("AssistanceRequests");
        firebaseStorage = FirebaseStorage.getInstance();
        assistancePhotosStorageReference = firebaseStorage.getReference().child("assistance_photos");

        // 2. VIEW BINDING
        tvBus = findViewById(R.id.tvMenuBusNo);
        tvDriver = findViewById(R.id.tvMenuDriver);
        tvConductor = findViewById(R.id.tvMenuConductor);
        tvCurrentLoop = findViewById(R.id.tvCurrentLoopDisplay);

        tvRegularPax = findViewById(R.id.tvRegularPax);
        tvStudentPax = findViewById(R.id.tvStudentPax);
        tvSeniorPax = findViewById(R.id.tvSeniorPax);
        tvTotalPax = findViewById(R.id.tvTotalPax);

        tvTotalCollected = findViewById(R.id.tvTotalCollected);
        tvTotalGcash = findViewById(R.id.tvTotalGcash);
        tvTotalCash = findViewById(R.id.tvTotalCash);

        btnNewTrip = findViewById(R.id.btnEndTrip);
        btnPrint = findViewById(R.id.btnPrintReport);
        btnViewData = findViewById(R.id.btnViewFareData);
        btnBack = findViewById(R.id.btnBackMenu);
        btnSettings = findViewById(R.id.btnSettings);
        btnHelp = findViewById(R.id.btnHelp);
        btnChat = findViewById(R.id.btnChat);

        SharedPreferences mainPrefs = getSharedPreferences("SantransPrefs", MODE_PRIVATE);
        currentSessionId = mainPrefs.getString("currentSessionId", "UNKNOWN");

        // 3. LISTENERS
        btnViewData.setOnClickListener(v -> startActivity(new Intent(ConductorMenuActivity.this, FareDataActivity.class)));

        btnNewTrip.setText("NEW TRIP / CLEAR DATA");
        btnNewTrip.setOnClickListener(v -> showSecureEndTripDialog());

        btnBack.setOnClickListener(v -> finish());
        btnPrint.setOnClickListener(v -> fetchAndGenerateReport());

        if (btnHelp != null) {
            btnHelp.setOnClickListener(v -> {
                if (isEmergencyActive) showResolveDialog(); else showAssistanceDialog();
            });
        }

        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> {
                startActivity(new Intent(ConductorMenuActivity.this, SettingsActivity.class));
            });
        }

        if (btnChat != null) {
            btnChat.setOnClickListener(v -> {
                startActivity(new Intent(ConductorMenuActivity.this, ChatActivity.class));
            });
        }

        loadHeaderInfo();
    }

    // ... (rest of the methods from onCreate down to onPause)
    private void showSecureEndTripDialog() {
        if (isFinishing()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("⚠️ END TRIP & CLEAR DATA?");
        builder.setMessage("This will RESET the current trip data on this device.\n(Cloud Data is safe)\n\nVerification Required:");
        builder.setIcon(android.R.drawable.ic_dialog_alert);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 30, 60, 20);

        final EditText inputInspector = new EditText(this);
        inputInspector.setHint("Inspector / Dispatcher Name");
        layout.addView(inputInspector);

        final EditText inputPass = new EditText(this);
        inputPass.setHint("Admin Passcode");
        inputPass.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        layout.addView(inputPass);

        builder.setView(layout);

        builder.setPositiveButton("CONFIRM RESET", (dialog, which) -> {
            String inspector = inputInspector.getText().toString().trim();
            String passcode = inputPass.getText().toString().trim();

            if (!passcode.equals(ADMIN_PASSCODE)) {
                Toast.makeText(this, "❌ Incorrect Passcode!", Toast.LENGTH_LONG).show();
                return;
            }
            if (inspector.isEmpty()) {
                Toast.makeText(this, "⚠️ Inspector Name Required!", Toast.LENGTH_SHORT).show();
                return;
            }
            performLocalPhoneReset();
        });

        builder.setNegativeButton("CANCEL", null);
        builder.setCancelable(false);
        builder.show();
    }

    private void performLocalPhoneReset() {
        Toast.makeText(this, "Calibrating new trip...", Toast.LENGTH_SHORT).show();

        deviceRef.child("LiveStatus").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    SharedPreferences offsetPrefs = getSharedPreferences("BusDataOffsets", MODE_PRIVATE);
                    SharedPreferences.Editor editor = offsetPrefs.edit();

                    editor.putInt("offset_reg", parseSnapshotInt(snapshot.child("regularCount")));
                    editor.putInt("offset_stu", parseSnapshotInt(snapshot.child("studentCount")));
                    editor.putInt("offset_sen", parseSnapshotInt(snapshot.child("seniorCount")));
                    editor.putFloat("offset_cash", (float) parseSnapshotDouble(snapshot.child("totalCash")));
                    editor.putFloat("offset_gcash", (float) parseSnapshotDouble(snapshot.child("totalGcash")));
                    editor.apply();
                }

                if (syncListener != null) {
                    deviceRef.child("LiveStatus").removeEventListener(syncListener);
                }

                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                String newSessionID = "TRIP_" + timeStamp;

                SharedPreferences prefs = getSharedPreferences("SantransPrefs", MODE_PRIVATE);
                prefs.edit().putString("currentSessionId", newSessionID).apply();

                SharedPreferences stats = getSharedPreferences("BusStats", MODE_PRIVATE);
                stats.edit().clear().apply();

                getSharedPreferences("TicketCounter", MODE_PRIVATE)
                        .edit().putInt("last_ticket_count", 0).apply();

                BusNotificationManager notifManager = new BusNotificationManager(getApplicationContext());
                notifManager.resetStats();

                updateUI(0, 0, 0, 0, 0.0, 0.0, 0.0, "READY FOR NEW TRIP");

                Toast.makeText(ConductorMenuActivity.this, "✅ TRIP ENDED! Ready for new passengers.", Toast.LENGTH_LONG).show();

                Intent intent = new Intent(ConductorMenuActivity.this, RouteListActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ConductorMenuActivity.this, "Reset Failed: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private int parseSnapshotInt(DataSnapshot snap) {
        if (snap.getValue() instanceof Number) return ((Number) snap.getValue()).intValue();
        return 0;
    }
    private double parseSnapshotDouble(DataSnapshot snap) {
        if (snap.getValue() instanceof Number) return ((Number) snap.getValue()).doubleValue();
        return 0.0;
    }

    @Override
    protected void onResume() {
        super.onResume();
        startRealTimeSync();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (syncListener != null) {
            deviceRef.child("LiveStatus").removeEventListener(syncListener);
        }
    }

    private void startRealTimeSync() {
        syncListener = deviceRef.child("LiveStatus").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    int liveReg = parseSnapshotInt(snapshot.child("regularCount"));
                    int liveStu = parseSnapshotInt(snapshot.child("studentCount"));
                    int liveSen = parseSnapshotInt(snapshot.child("seniorCount"));
                    double liveCash = parseSnapshotDouble(snapshot.child("totalCash"));
                    double liveGcash = parseSnapshotDouble(snapshot.child("totalGcash"));

                    SharedPreferences offsets = getSharedPreferences("BusDataOffsets", MODE_PRIVATE);
                    int offReg = offsets.getInt("offset_reg", 0);
                    int offStu = offsets.getInt("offset_stu", 0);
                    int offSen = offsets.getInt("offset_sen", 0);
                    double offCash = offsets.getFloat("offset_cash", 0);
                    double offGcash = offsets.getFloat("offset_gcash", 0);

                    int reg = Math.max(0, liveReg - offReg);
                    int stu = Math.max(0, liveStu - offStu);
                    int sen = Math.max(0, liveSen - offSen);
                    double cash = Math.max(0.0, liveCash - offCash);
                    double gcash = Math.max(0.0, liveGcash - offGcash);

                    int totalPax = reg + stu + sen;
                    double totalCollected = cash + gcash;

                    String rawLoop = snapshot.child("currentLoop").getValue(String.class);
                    String displayLoop = "FVR to ST.CRUZ"; // Default
                    if (rawLoop != null && !rawLoop.isEmpty() && !rawLoop.equalsIgnoreCase("N/A")) {
                        displayLoop = rawLoop;
                    }

                    Boolean emergencyStatus = snapshot.child("emergencyStatus").getValue(Boolean.class);
                    isEmergencyActive = (emergencyStatus != null && emergencyStatus);

                    if (btnHelp != null) {
                        btnHelp.setColorFilter(isEmergencyActive ? Color.parseColor("#FF0000") : Color.parseColor("#FF1744"));
                    }

                    updateUI(reg, stu, sen, totalPax, cash, gcash, totalCollected, displayLoop);
                } else {
                    updateUI(0, 0, 0, 0, 0.0, 0.0, 0.0, "FVR to ST.CRUZ");
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void updateUI(int reg, int stu, int sen, int totalPax, double cash, double gcash, double total, String loop) {
        tvRegularPax.setText(String.valueOf(reg));
        tvStudentPax.setText(String.valueOf(stu));
        tvSeniorPax.setText(String.valueOf(sen));
        tvTotalPax.setText(String.valueOf(totalPax));
        if (tvCurrentLoop != null) tvCurrentLoop.setText(loop);
        tvTotalCollected.setText("₱" + String.format("%.2f", total));
        tvTotalGcash.setText("₱" + String.format("%.2f", gcash));
        tvTotalCash.setText("₱" + String.format("%.2f", cash));
    }

    private void fetchAndGenerateReport() {
        Toast.makeText(this, "Generating Report...", Toast.LENGTH_SHORT).show();

        SharedPreferences prefs = getSharedPreferences("SantransPrefs", MODE_PRIVATE);
        currentSessionId = prefs.getString("currentSessionId", "UNKNOWN");

        if(currentSessionId.equals("UNKNOWN")) {
            Toast.makeText(this, "No Active Session.", Toast.LENGTH_SHORT).show();
            return;
        }

        DatabaseReference transRef = deviceRef.child("Trips").child(currentSessionId).child("Transactions");
        transRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Toast.makeText(ConductorMenuActivity.this, "No data for this trip yet.", Toast.LENGTH_LONG).show();
                    return;
                }
                processDataAndPrint(snapshot);
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ConductorMenuActivity.this, "DB Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void processDataAndPrint(DataSnapshot snapshot) {
        try {
            int regCount = 0, studCount = 0, seniorCount = 0, totalPax = 0;
            double totalCash = 0.0, totalGcash = 0.0, totalCollection = 0.0;
            HashMap<String, Integer> routeMap = new HashMap<>();

            for (DataSnapshot ticketSnap : snapshot.getChildren()) {
                String type = ticketSnap.child("passengerType").getValue(String.class);
                String pMethod = ticketSnap.child("paymentMethod").getValue(String.class);
                String origin = ticketSnap.child("origin").getValue(String.class);
                String dest = ticketSnap.child("destination").getValue(String.class);

                int count = 1;
                if (ticketSnap.child("passengerCount").getValue() instanceof Number)
                    count = ((Number) ticketSnap.child("passengerCount").getValue()).intValue();

                double amount = 0.0;
                if (ticketSnap.child("totalAmount").getValue() instanceof Number)
                    amount = ((Number) ticketSnap.child("totalAmount").getValue()).doubleValue();
                else if (ticketSnap.child("totalFare").getValue() instanceof Number)
                    amount = ((Number) ticketSnap.child("totalFare").getValue()).doubleValue();

                totalPax += count;
                totalCollection += amount;

                if (type != null) {
                    if (type.equalsIgnoreCase("Regular")) regCount += count;
                    else if (type.equalsIgnoreCase("Student")) studCount += count;
                    else seniorCount += count;
                }

                if (pMethod != null && pMethod.toUpperCase().contains("GCASH")) totalGcash += amount;
                else totalCash += amount;

                if (origin != null && dest != null) {
                    String routeKey = origin + " -> " + dest;
                    routeMap.put(routeKey, routeMap.getOrDefault(routeKey, 0) + count);
                }
            }
            createPdfReport(regCount, studCount, seniorCount, totalPax, totalCash, totalGcash, totalCollection, routeMap);
        } catch (Exception e) {
            Toast.makeText(this, "Error processing: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void createPdfReport(int reg, int stud, int sen, int totPax, double cash, double gcash, double totalCol, HashMap<String, Integer> routeMap) {
        try {
            File docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            File mainDir = new File(docsDir, "POS BUS TICKETING SIMULATION");
            if (!mainDir.exists()) mainDir.mkdirs();

            File reportDir = new File(mainDir, "REPORTS");
            if (!reportDir.exists()) reportDir.mkdirs();

            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File pdfFile = new File(reportDir, "WAYBILL_" + timeStamp + ".pdf");

            PdfDocument document = new PdfDocument();
            int width = 400;
            int routeRows = routeMap.size();
            int height = 900 + (routeRows * 20);

            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(width, height, 1).create();
            PdfDocument.Page page = document.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            Paint titlePaint = new Paint(); titlePaint.setTextSize(18); titlePaint.setTypeface(Typeface.DEFAULT_BOLD); titlePaint.setTextAlign(Paint.Align.CENTER);
            Paint subTitlePaint = new Paint(); subTitlePaint.setTextSize(12); subTitlePaint.setTextAlign(Paint.Align.CENTER);
            Paint boldPaint = new Paint(); boldPaint.setTextSize(12); boldPaint.setTypeface(Typeface.DEFAULT_BOLD); boldPaint.setTextAlign(Paint.Align.LEFT);
            Paint normalPaint = new Paint(); normalPaint.setTextSize(12); normalPaint.setTextAlign(Paint.Align.LEFT);
            Paint rightPaint = new Paint(); rightPaint.setTextSize(12); rightPaint.setTextAlign(Paint.Align.RIGHT);
            Paint linePaint = new Paint(); linePaint.setStrokeWidth(2);

            int xCenter = width / 2;
            int xL = 25, xR = width - 25;
            int y = 50;

            canvas.drawText("POS BUS TICKETING SIMULATION", xCenter, y, titlePaint); y+=20;
            canvas.drawText("Bus Ticketing System", xCenter, y, subTitlePaint); y+=30;

            Paint borderPaint = new Paint();
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(2);
            canvas.drawRect(10, 10, width-10, height-10, borderPaint);

            String dateNow = new SimpleDateFormat("MMM dd, yyyy  hh:mm a", Locale.US).format(new Date());

            canvas.drawLine(xL, y, xR, y, linePaint); y+=20;
            drawRow(canvas, "DATE:", dateNow, xL, xR, y, boldPaint, rightPaint); y+=20;
            drawRow(canvas, "TRIP ID:", currentSessionId, xL, xR, y, normalPaint, rightPaint); y+=20;
            drawRow(canvas, "BUS NO:", tvBus.getText().toString().replace("BUS #: ", ""), xL, xR, y, boldPaint, rightPaint); y+=20;
            canvas.drawLine(xL, y, xR, y, linePaint); y+=25;

            canvas.drawText("CREW INFORMATION", xL, y, boldPaint); y+=20;
            canvas.drawText("Driver: " + tvDriver.getText().toString().replace("Driver: ", ""), xL, y, normalPaint); y+=18;
            canvas.drawText("Conductor: " + tvConductor.getText().toString().replace("Conductor: ", ""), xL, y, normalPaint); y+=25;

            canvas.drawText("PASSENGER SUMMARY", xL, y, boldPaint); y+=10;
            canvas.drawLine(xL, y, xR, y, linePaint); y+=20;
            drawRow(canvas, "Regular", String.valueOf(reg), xL, xR, y, normalPaint, rightPaint); y+=18;
            drawRow(canvas, "Student (20%)", String.valueOf(stud), xL, xR, y, normalPaint, rightPaint); y+=18;
            drawRow(canvas, "Senior/PWD (20%)", String.valueOf(sen), xL, xR, y, normalPaint, rightPaint); y+=18;
            canvas.drawLine(xL, y, xR, y, linePaint); y+=20;
            drawRow(canvas, "TOTAL PASSENGERS", String.valueOf(totPax), xL, xR, y, boldPaint, rightPaint); y+=30;

            canvas.drawText("ROUTE BREAKDOWN (Top List)", xL, y, boldPaint); y+=20;
            List<String> sortedRoutes = new ArrayList<>(routeMap.keySet());
            Collections.sort(sortedRoutes);
            for (String route : sortedRoutes) {
                String displayName = route.length() > 30 ? route.substring(0, 27) + "..." : route;
                drawRow(canvas, displayName, String.valueOf(routeMap.get(route)), xL, xR, y, normalPaint, rightPaint);
                y+=18;
            }
            y+=15;

            canvas.drawRect(xL, y, xR, y + 90, borderPaint);
            y+=25;
            drawRow(canvas, "CASH Sales:", "P " + String.format("%.2f", cash), xL+10, xR-10, y, normalPaint, rightPaint); y+=20;
            drawRow(canvas, "GCASH Sales:", "P " + String.format("%.2f", gcash), xL+10, xR-10, y, normalPaint, rightPaint); y+=25;

            Paint totalPaint = new Paint(); totalPaint.setTextSize(16); totalPaint.setTypeface(Typeface.DEFAULT_BOLD); totalPaint.setTextAlign(Paint.Align.RIGHT);
            Paint totalLabel = new Paint(); totalLabel.setTextSize(16); totalLabel.setTypeface(Typeface.DEFAULT_BOLD); totalLabel.setTextAlign(Paint.Align.LEFT);

            canvas.drawText("TOTAL:", xL+10, y, totalLabel);
            canvas.drawText("P " + String.format("%.2f", totalCol), xR-10, y, totalPaint); y+=50;

            canvas.drawText("_______________________", xL, y, normalPaint);
            canvas.drawText("_______________________", xCenter + 20, y, normalPaint);
            y+=15;

            canvas.drawText("Conductor's Signature", xL, y, normalPaint);
            canvas.drawText("Inspector's Signature", xCenter + 20, y, normalPaint);
            y+=40;

            String footer = "System Generated Report - DO NOT LOSE";
            Paint footerPaint = new Paint(); footerPaint.setTextSize(10); footerPaint.setTextAlign(Paint.Align.CENTER); footerPaint.setColor(Color.GRAY);
            canvas.drawText(footer, xCenter, height - 20, footerPaint);

            document.finishPage(page);
            FileOutputStream fos = new FileOutputStream(pdfFile);
            document.writeTo(fos);
            document.close();
            fos.close();

            MediaScannerConnection.scanFile(this, new String[]{pdfFile.toString()}, null, null);
            Toast.makeText(this, "✅ REPORT SAVED!", Toast.LENGTH_LONG).show();
            showPrintNotification(pdfFile);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "❌ Error saving PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void drawRow(Canvas c, String label, String val, int xL, int xR, int y, Paint pL, Paint pR) {
        c.drawText(label, xL, y, pL);
        c.drawText(val, xR, y, pR);
    }

    private void showPrintNotification(File pdfFile) {
        String channelId = "print_notifications";
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Print Reports", NotificationManager.IMPORTANCE_HIGH);
            if (notificationManager != null) notificationManager.createNotificationChannel(channel);
        }

        try {
            Uri fileUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", pdfFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(fileUri, "application/pdf");
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NO_HISTORY);

            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle("Waybill Generated")
                    .setContentText("Tap to view: " + pdfFile.getName())
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true);

            if (notificationManager != null) notificationManager.notify(200, builder.build());

        } catch (Exception e) {
            // Ignore intent errors
        }
    }

    private void showAssistanceDialog() {
        String[] emergencies = {
                "🔧 Mechanical Breakdown", "🛑 Flat Tire", "💥 Road Accident",
                "🚑 Medical Emergency", "👮 Security Issue", "🚦 Heavy Traffic",
                "🌊 Flooded Road", "🖥️ System Error", "⛽ Low Fuel"
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🚨 REQUEST ASSISTANCE");
        builder.setItems(emergencies, (dialog, which) -> confirmHelpRequest(emergencies[which]));
        builder.setNegativeButton("CANCEL", null);
        builder.show();
    }

    private void confirmHelpRequest(String issue) {
        if(isFinishing()) return;
        new AlertDialog.Builder(this)
                .setTitle("⚠️ REPORT ISSUE?")
                .setMessage("Report to HQ: " + issue)
                .setNeutralButton("Attach Photo", (d, w) -> {
                    pendingAssistanceIssue = issue;
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE_ASSISTANCE);
                    } else {
                        openAssistanceImagePicker();
                    }
                })
                .setPositiveButton("SEND ALERT ONLY", (dialog, which) -> sendHelpRequest(issue, null))
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private void openAssistanceImagePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/jpeg");
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), RC_ASSISTANCE_PHOTO_PICKER);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE_ASSISTANCE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openAssistanceImagePicker();
            } else {
                Toast.makeText(this, "Permission denied. Cannot attach photo.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_ASSISTANCE_PHOTO_PICKER && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                Uri selectedImageUri = data.getData();
                uploadAssistancePhoto(selectedImageUri);
            } else {
                Toast.makeText(this, "Failed to get image", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void uploadAssistancePhoto(Uri imageUri) {
        Toast.makeText(this, "Uploading photo...", Toast.LENGTH_SHORT).show();
        final StorageReference photoRef = assistancePhotosStorageReference.child(imageUri.getLastPathSegment() + "_" + System.currentTimeMillis());

        photoRef.putFile(imageUri).addOnSuccessListener(this, taskSnapshot -> {
            photoRef.getDownloadUrl().addOnSuccessListener(uri -> {
                sendHelpRequest(pendingAssistanceIssue, uri.toString());
                pendingAssistanceIssue = ""; // Clear pending issue
            });
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }


    private void sendHelpRequest(String issue, @Nullable String imageUrl) {
        String busNumber = tvBus.getText().toString().replace("BUS #: ", "");
        Map<String, Object> request = new HashMap<>();
        request.put("deviceSerial", deviceSerial);
        request.put("busNumber", busNumber);
        request.put("issue", issue);
        request.put("status", "OPEN");
        request.put("timestamp", ServerValue.TIMESTAMP);
        if (imageUrl != null) {
            request.put("imageUrl", imageUrl);
        }

        assistanceRef.child(busNumber).setValue(request);
        deviceRef.child("LiveStatus").child("emergencyStatus").setValue(true);
        deviceRef.child("LiveStatus").child("emergencyReason").setValue(issue);

        Toast.makeText(this, "🚨 ALERT SENT!", Toast.LENGTH_LONG).show();
    }

    private void showResolveDialog() {
        if(isFinishing()) return;
        new AlertDialog.Builder(this)
                .setTitle("✅ ISSUE RESOLVED?")
                .setMessage("Resume normal operations?")
                .setPositiveButton("YES", (dialog, which) -> resolveEmergency())
                .setNegativeButton("NO", null)
                .show();
    }

    private void resolveEmergency() {
        String busNumber = tvBus.getText().toString().replace("BUS #: ", "");
        assistanceRef.child(busNumber).child("status").setValue("RESOLVED");
        deviceRef.child("LiveStatus").child("emergencyStatus").setValue(false);
        deviceRef.child("LiveStatus").child("emergencyReason").setValue(null);
    }

    private void loadHeaderInfo() {
        SharedPreferences mainPrefs = getSharedPreferences("SantransPrefs", MODE_PRIVATE);
        tvBus.setText("BUS #: " + mainPrefs.getString("busNumber", "---"));
        tvDriver.setText("Driver: " + mainPrefs.getString("driverName", "---"));
        tvConductor.setText("Conductor: " + mainPrefs.getString("conductorName", "---"));
    }
}
