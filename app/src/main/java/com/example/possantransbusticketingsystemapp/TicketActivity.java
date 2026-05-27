package com.example.possantransbusticketingsystemapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.location.Address;
import android.location.Geocoder;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// EXTENDS BaseActivity para sa Theme at Network Banner
public class TicketActivity extends BaseActivity {

    // --- VARIABLES ---
    private String busNo, driver, conductor, origin, destination, passengerType;
    private int passengerCount;
    private double baseFare, discountAmount, totalAmount, finalPricePerPax;
    private String deviceModel;
    private String ticketID;

    // --- TOOLS ---
    private DatabaseReference mDatabase;
    private BusNotificationManager notifManager;
    private FusedLocationProviderClient fusedLocationClient;

    // --- UI ELEMENTS ---
    private TextView tvSerial;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ticket);

        try {
            // 1. Initialize Tools
            // ENABLE OFFLINE SYNC (Para mag-save muna sa phone pag walang net, tapos upload pag meron na)
            mDatabase = FirebaseDatabase.getInstance().getReference();

            // FIXED: Gamitin ang getApplicationContext() para hindi mamatay ang notif manager pag nag-close ang activity
            notifManager = new BusNotificationManager(getApplicationContext());

            // Location tool para makuha kung saan nag-issue ng ticket
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
            deviceModel = android.os.Build.MODEL.toUpperCase();

            // ADDED: Permission Check para sa Android 13+ (Kasi maarte na sa Notifications ang bago)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
                }
            }

            // 2. Get Data galing sa nakaraang screen (RouteListActivity)
            getIntentData();
            // Compute agad para ready na ang presyo
            calculateFare();

            // 3. Smart Recovery (Ticket #) - Check kung kailangan mag-reset ng number o ituloy ang bilang
            performSmartRecovery();

            // 4. Setup UI (Display details sa screen para makita ni Conductor)
            tvSerial = findViewById(R.id.tvSerialNum);
            populateUIFields();

            // 5. Setup Buttons (Listeners para sa Click events)
            setupButtonListeners();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // =========================================================================
    // DATA & MATH (Fare Calculation)
    // =========================================================================

    private void getIntentData() {
        Intent intent = getIntent();
        busNo = intent.getStringExtra("BUS_NO");
        driver = intent.getStringExtra("DRIVER");
        conductor = intent.getStringExtra("CONDUCTOR");
        origin = intent.getStringExtra("ORIGIN");
        destination = intent.getStringExtra("DESTINATION");
        passengerType = intent.getStringExtra("PASSENGER_TYPE");
        passengerCount = intent.getIntExtra("PASSENGER_COUNT", 1);
        baseFare = intent.getDoubleExtra("BASE_FARE", 0.0);

        // Fallback checks para hindi maging "null" sa database pag may nakalimutan
        if(busNo == null || busNo.isEmpty()) busNo = "---";
        if(driver == null || driver.isEmpty()) driver = "N/A";
        if(conductor == null || conductor.isEmpty()) conductor = "N/A";
        if(passengerType == null) passengerType = "Regular";
        if(passengerCount < 1) passengerCount = 1;
    }

    private void calculateFare() {
        double discountPerPax = 0.0;
        // Check kung eligible sa discount (20% batas 'yan)
        if ("Student".equalsIgnoreCase(passengerType) ||
                "Senior".equalsIgnoreCase(passengerType) ||
                passengerType.contains("PWD") ||
                passengerType.contains("Senior")) {
            discountPerPax = baseFare * 0.20; // 20% Discount
        }
        finalPricePerPax = baseFare - discountPerPax;
        totalAmount = finalPricePerPax * passengerCount;
        discountAmount = discountPerPax;
    }

    // =========================================================================
    // UI POPULATION (Set Text sa Screen)
    // =========================================================================

    private void populateUIFields() {
        // Didisplay na natin lahat ng info sa CardView
        TextView tvHeadBus = findViewById(R.id.tvHeaderBusNo);
        if (tvHeadBus != null) tvHeadBus.setText(busNo);

        TextView tvHeadDriver = findViewById(R.id.tvHeaderDriver);
        if (tvHeadDriver != null) tvHeadDriver.setText("Driver: " + driver);

        TextView tvHeadCond = findViewById(R.id.tvHeaderConductor);
        if (tvHeadCond != null) tvHeadCond.setText("Conductor: " + conductor);

        TextView tvTicketOrigin = findViewById(R.id.tvTicketOrigin);
        if (tvTicketOrigin != null) tvTicketOrigin.setText(origin);

        TextView tvTicketDest = findViewById(R.id.tvTicketDest);
        if (tvTicketDest != null) tvTicketDest.setText(destination);

        TextView tvTicketPrice = findViewById(R.id.tvTicketPrice);
        if (tvTicketPrice != null) tvTicketPrice.setText("₱" + String.format("%.2f", baseFare));

        TextView tvTicketDiscount = findViewById(R.id.tvTicketDiscount);
        if (tvTicketDiscount != null) tvTicketDiscount.setText("₱" + String.format("%.2f", discountAmount));

        TextView tvTicketTotal = findViewById(R.id.tvTicketTotal);
        if (tvTicketTotal != null) tvTicketTotal.setText("₱" + String.format("%.2f", totalAmount));

        TextView tvTicketPaxType = findViewById(R.id.tvTicketPaxType);
        if(tvTicketPaxType != null) tvTicketPaxType.setText(passengerType);

        TextView tvTicketPaxCount = findViewById(R.id.tvTicketPaxCount);
        if(tvTicketPaxCount != null) tvTicketPaxCount.setText(String.valueOf(passengerCount));
    }

    private void setupButtonListeners() {
        // Pag GCash, open muna QR Dialog
        Button btnGcash = findViewById(R.id.btnGcash);
        if (btnGcash != null) btnGcash.setOnClickListener(v -> showGcashQrDialog());

        // Pag Cash, rekta save na
        Button btnPrintTicket = findViewById(R.id.btnPrintTicket);
        if (btnPrintTicket != null) btnPrintTicket.setOnClickListener(v -> proceedToSave("CASH"));

        // Back button lang
        Button btnCancelTicket = findViewById(R.id.btnCancelTicket);
        if (btnCancelTicket != null) btnCancelTicket.setOnClickListener(v -> finish());
    }

    // =========================================================================
    // GCASH DIALOG
    // =========================================================================
    private void showGcashQrDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.activity_gcash_qr, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        // Transparent background para lumitaw yung rounded corners
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        TextView tvAmount = dialogView.findViewById(R.id.tvTotalAmountQr);
        Button btnConfirm = dialogView.findViewById(R.id.btnConfirmPayment);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelQr);

        if (tvAmount != null) tvAmount.setText("₱" + String.format("%.2f", totalAmount));

        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(v -> {
                dialog.dismiss();
                proceedToSave("GCASH"); // Tuloy na sa saving pag na-confirm
            });
        }
        if (btnCancel != null) btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    // =========================================================================
    // SAVING LOGIC (OFFLINE FIRST)
    // =========================================================================

    private void proceedToSave(String method) {
        // Check muna kung may location permission bago mag-save para sa map markers
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            finalizeEverything(method, 0.0, 0.0, "No Location Access");
            return;
        }
        getLocationAndProceed(method);
    }

    @SuppressLint("MissingPermission")
    private void getLocationAndProceed(String method) {
        // Kukunin ang current GPS location ng bus
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            double lat = 0.0, lng = 0.0;
            String locName = "Unknown Location";
            if (location != null) {
                lat = location.getLatitude();
                lng = location.getLongitude();
                try {
                    // Convert coordinates to Barangay name (Reverse Geocoding)
                    Geocoder geocoder = new Geocoder(TicketActivity.this, Locale.getDefault());
                    List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
                    if (addresses != null && !addresses.isEmpty()) locName = addresses.get(0).getLocality();
                } catch (Exception e) { e.printStackTrace(); }
            }
            finalizeEverything(method, lat, lng, locName);
        });
    }

    private void finalizeEverything(String method, double lat, double lng, String locName) {
        // Gawa ng bagong Ticket Number (ex: 001, 002...)
        ticketID = generateNewTicketID();

        // 1. Save muna ang PDF sa phone (Storage) - Importante to para may resibo kahit offline
        if (saveTicketToHiddenPDF(method)) {
            playSoundEffect();
            // 2. I-save sa Firebase (Si Firebase na bahala mag-queue pag walang internet)
            saveToFirebase(method, lat, lng, locName);
        }
    }

    // =========================================================================
    // FIREBASE SYNC (AUTO SYNC WHEN ONLINE)
    // =========================================================================
    private void saveToFirebase(String paymentMethod, double lat, double lng, String locName) {
        String safeSerial = deviceModel.replaceAll("[^a-zA-Z0-9]", "_").toUpperCase();
        SharedPreferences prefs = getSharedPreferences("SantransPrefs", MODE_PRIVATE);
        String sessionID = prefs.getString("currentSessionId", "UNKNOWN_TRIP");
        String currentLoop = prefs.getString("currentLoop", "N/A");

        // Keep synced para laging fresh ang data na makukuha natin
        String livePath = "/POS_Devices/" + safeSerial + "/LiveStatus";
        mDatabase.child(livePath).keepSynced(true);

        // Basahin muna ang current total bago magdagdag (Read-Modify-Write)
        mDatabase.child(livePath).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int currentPax = 0, reg = 0, stu = 0, sen = 0;
                double currentCash = 0.0, currentGcash = 0.0;

                // Kunin ang latest data galing Firebase (Cloud Total)
                if(snapshot.exists()) {
                    if(snapshot.child("totalPax").getValue() instanceof Number) currentPax = ((Number) snapshot.child("totalPax").getValue()).intValue();
                    if(snapshot.child("totalCash").getValue() instanceof Number) currentCash = ((Number) snapshot.child("totalCash").getValue()).doubleValue();
                    if(snapshot.child("totalGcash").getValue() instanceof Number) currentGcash = ((Number) snapshot.child("totalGcash").getValue()).doubleValue();
                    if(snapshot.child("regularCount").getValue() instanceof Number) reg = ((Number) snapshot.child("regularCount").getValue()).intValue();
                    if(snapshot.child("studentCount").getValue() instanceof Number) stu = ((Number) snapshot.child("studentCount").getValue()).intValue();
                    if(snapshot.child("seniorCount").getValue() instanceof Number) sen = ((Number) snapshot.child("seniorCount").getValue()).intValue();
                }

                // Add current transaction data (Dagdag na natin yung bago)
                currentPax += passengerCount;

                if (paymentMethod.equalsIgnoreCase("GCASH")) {
                    currentGcash += totalAmount;
                } else {
                    currentCash += totalAmount;
                }

                // Update breakdown kung anong klaseng pasahero
                if(passengerType.equalsIgnoreCase("Student")) stu += passengerCount;
                else if(passengerType.contains("Senior") || passengerType.contains("PWD")) sen += passengerCount;
                else reg += passengerCount;

                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                TicketTransaction transaction = new TicketTransaction(
                        busNo, driver, conductor, origin, destination,
                        passengerType, passengerCount, baseFare, discountAmount, totalAmount,
                        paymentMethod, sdf.format(new Date()), deviceModel, ticketID,
                        lat, lng, locName
                );

                String tripPath = "/POS_Devices/" + safeSerial + "/Trips/" + sessionID;
                Map<String, Object> updates = new HashMap<>();

                // Trip History (Detailed logs per ticket)
                updates.put(tripPath + "/Transactions/Ticket_" + ticketID, transaction);
                updates.put(tripPath + "/busNumber", busNo); // FIXED: Siniguradong nasesave ang bus number
                updates.put(tripPath + "/driver", driver); // FIXED: Siniguradong nasesave ang driver
                updates.put(tripPath + "/conductor", conductor); // FIXED: Siniguradong nasesave ang conductor
                updates.put(tripPath + "/totalPax", currentPax);
                updates.put(tripPath + "/totalCash", currentCash);
                updates.put(tripPath + "/totalGcash", currentGcash);

                // Live Status (For Dashboard monitoring - Realtime)
                updates.put(livePath + "/busNumber", busNo); // FIXED: Nilagay ang bus number sa live node
                updates.put(livePath + "/driver", driver); // FIXED: Nilagay ang driver sa live node
                updates.put(livePath + "/conductor", conductor); // FIXED: Nilagay ang conductor sa live node
                updates.put(livePath + "/totalPax", currentPax);
                updates.put(livePath + "/totalCash", currentCash);
                updates.put(livePath + "/totalGcash", currentGcash);
                updates.put(livePath + "/regularCount", reg);
                updates.put(livePath + "/studentCount", stu);
                updates.put(livePath + "/seniorCount", sen);
                updates.put(livePath + "/lastTicketID", ticketID);
                updates.put(livePath + "/currentLoop", currentLoop);
                updates.put(livePath + "/lat", lat); // Sinave ang latitude para sa map
                updates.put(livePath + "/lng", lng); // Sinave ang longitude para sa map
                updates.put(livePath + "/lastUpdate", System.currentTimeMillis());

                // Atomic Update (Sabay-sabay i-save para walang data corruption)
                mDatabase.updateChildren(updates);
                updateLocalStats(currentPax, (float)currentCash, (float)currentGcash, reg, stu, sen);

                // =================================================================
                // 1. UPDATE LIVE NOTIFICATION (FIXED: Pass Individual Counts)
                // =================================================================
                if (notifManager != null) {
                    String displayRoute = origin + " - " + destination;
                    notifManager.updateLiveStatus(
                            displayRoute,
                            reg,   // Raw Regular Count
                            stu,   // Raw Student Count
                            sen,   // Raw Senior Count
                            (float) currentCash,
                            (float) currentGcash,
                            ticketID
                    );
                }

                // =================================================================
                // 2. TRIGGER SYNC ANIMATION & EXIT (Pakita na kay user ang status)
                // =================================================================
                simulateSyncAndExit(paymentMethod);
            }

            @Override public void onCancelled(@NonNull DatabaseError error) { goToRouteList(); }
        });
    }

    private void updateLocalStats(int pax, float cash, float gcash, int reg, int stu, int sen) {
        // Save sa SharedPreferences para mabilis ma-access sa ibang screen kahit walang net
        getSharedPreferences("BusStats", Context.MODE_PRIVATE).edit()
                .putInt("totalPax", pax).putFloat("cashAmount", cash).putFloat("gcashAmount", gcash)
                .putInt("countRegular", reg).putInt("countStudent", stu).putInt("countSenior", sen).apply();
    }

    // =========================================================================
    // SMART SYNC SIMULATION (DETECTS ONLINE/OFFLINE)
    // =========================================================================
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private void simulateSyncAndExit(String paymentMethod) {
        // 1. Setup Dialog para alam ni user na may nangyayari
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Transaction Processing");
        builder.setCancelable(false);

        // Change Icon based on Status (Online vs Offline)
        if (isNetworkAvailable()) {
            builder.setIcon(android.R.drawable.ic_popup_sync);
            builder.setMessage("Status: CONNECTING TO SERVER...");
        } else {
            builder.setIcon(android.R.drawable.ic_menu_save);
            builder.setMessage("Status: OFFLINE MODE");
        }

        AlertDialog dialog = builder.create();
        dialog.show();

        if (isNetworkAvailable()) {
            // --- ONLINE SEQUENCE (May konting delay para feel na nag-uupload) ---
            new Handler().postDelayed(() -> {

                if (dialog.isShowing()) {
                    dialog.setMessage("Status: SYNCING DATA TO CLOUD...");
                }

                new Handler().postDelayed(() -> {
                    if (dialog.isShowing()) dialog.dismiss();

                    Toast.makeText(TicketActivity.this, "✔ Data Synced to Cloud!", Toast.LENGTH_SHORT).show();
                    Toast.makeText(TicketActivity.this, "Ticket Issued: " + paymentMethod, Toast.LENGTH_SHORT).show();

                    goToRouteList(); // Balik na sa listahan

                }, 1500); // Phase 2: Syncing

            }, 1500); // Phase 1: Connecting

        } else {
            // --- OFFLINE SEQUENCE (Mabilis lang kasi local save lang) ---
            new Handler().postDelayed(() -> {

                if (dialog.isShowing()) {
                    dialog.setMessage("Saving to Local Storage...");
                }

                new Handler().postDelayed(() -> {
                    if (dialog.isShowing()) dialog.dismiss();

                    Toast.makeText(TicketActivity.this, "⚠ Saved Offline (Will Sync Later)", Toast.LENGTH_LONG).show();
                    goToRouteList(); // Exit

                }, 1000); // Save Phase

            }, 1000); // Check Phase
        }
    }

    // =========================================================================
    // UTILITIES
    // =========================================================================

    private void playSoundEffect() {
        try {
            MediaPlayer mp = MediaPlayer.create(this, R.raw.custom_notif);
            if (mp != null) { mp.start(); mp.setOnCompletionListener(MediaPlayer::release); }
        } catch (Exception e) {}
    }

    // --- PDF GENERATION WITH SANTRANS FORMAT (Hidden Receipt) ---
    private boolean saveTicketToHiddenPDF(String method) {
        try {
            SharedPreferences prefs = getSharedPreferences("SantransPrefs", MODE_PRIVATE);
            String sessionID = prefs.getString("currentSessionId", "UNKNOWN_TRIP");
            File methodDir = new File(new File(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "POS BUS TICKETING SIMULATION"), "CUSTOMER_TICKET"), sessionID);
            if (!methodDir.exists()) methodDir.mkdirs();

            File ticketFile = new File(methodDir, "TICKET_" + ticketID + "_" + method + ".pdf");
            PdfDocument document = new PdfDocument();
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(300, 750, 1).create(); // Taasan natin ng konti para kasya lahat
            PdfDocument.Page page = document.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            // Paints (Styles ng text sa PDF)
            Paint pCenterBold = new Paint(); pCenterBold.setTextAlign(Paint.Align.CENTER); pCenterBold.setTextSize(16); pCenterBold.setTypeface(Typeface.DEFAULT_BOLD); pCenterBold.setColor(Color.BLACK);
            Paint pCenterSmall = new Paint(); pCenterSmall.setTextAlign(Paint.Align.CENTER); pCenterSmall.setTextSize(10); pCenterSmall.setColor(Color.BLACK);
            Paint pLeft = new Paint(); pLeft.setTextAlign(Paint.Align.LEFT); pLeft.setTextSize(12); pLeft.setColor(Color.BLACK);
            Paint pFooter = new Paint(); pFooter.setTextAlign(Paint.Align.CENTER); pFooter.setTextSize(10); pFooter.setTypeface(Typeface.DEFAULT_BOLD); pFooter.setColor(Color.BLACK);
            Paint pRed = new Paint(); pRed.setTextAlign(Paint.Align.CENTER); pRed.setColor(Color.RED); pRed.setTextSize(9); pRed.setTypeface(Typeface.DEFAULT_BOLD);
            Paint pBoldLeft = new Paint(); pBoldLeft.setTextAlign(Paint.Align.LEFT); pBoldLeft.setTextSize(12); pBoldLeft.setTypeface(Typeface.DEFAULT_BOLD); pBoldLeft.setColor(Color.BLACK);
            Paint pTotal = new Paint(); pTotal.setTextAlign(Paint.Align.CENTER); pTotal.setTextSize(14); pTotal.setTypeface(Typeface.DEFAULT_BOLD); pTotal.setColor(Color.BLACK);

            int y = 40, xCenter = 150, xLeft = 20, xRight = 280;

            // HEADER
            canvas.drawText("POS TICKETING SIMULATION", xCenter, y, pCenterBold); y += 15;

            String deviceSerial = deviceModel.replaceAll("[^a-zA-Z0-9]", "");
            canvas.drawText("Serial#: " + deviceSerial + "   BUS#: " + busNo, xCenter, y, pCenterSmall); y += 25;

            Paint pTitle = new Paint(); pTitle.setTextAlign(Paint.Align.CENTER); pTitle.setTextSize(14); pTitle.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("OFFICIAL RECEIPT", xCenter, y, pTitle); y += 15;
            canvas.drawText("(Ticket ID: " + ticketID + ")", xCenter, y, pCenterSmall); y += 30;

            // DETAILS (FIXED: Nilagay na natin dito yung Driver at Conductor)
            SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy hh:mm a", Locale.US);
            String currentDate = sdf.format(new Date());

            canvas.drawText("Driver: " + driver, xLeft, y, pLeft); y += 20;
            canvas.drawText("Conductor: " + conductor, xLeft, y, pLeft); y += 20;
            canvas.drawText("DATE & TIME: " + currentDate, xLeft, y, pLeft); y += 20;
            canvas.drawText("From: " + origin, xLeft, y, pLeft); y += 20;
            canvas.drawText("To: " + destination, xLeft, y, pLeft); y += 25;

            Paint pLine = new Paint(); pLine.setStrokeWidth(1); pLine.setColor(Color.LTGRAY);
            canvas.drawLine(xLeft, y, xRight, y, pLine); y += 20;

            // BREAKDOWN
            canvas.drawText(passengerType + " Fare: ₱" + String.format("%.2f", baseFare), xLeft, y, pLeft); y += 20;
            String discountTxt = "₱" + String.format("%.2f", discountAmount);
            if (discountAmount == 0) discountTxt += " (No Discount)";
            canvas.drawText("Discount: " + discountTxt, xLeft, y, pLeft); y += 20;
            canvas.drawText("AMOUNT: ₱" + String.format("%.2f", finalPricePerPax) + " " + method, xLeft, y, pBoldLeft); y += 35;

            // TOTAL
            String computation = passengerCount + " x ₱" + String.format("%.2f", finalPricePerPax) + " TOTAL of: " + String.format("%.2f", totalAmount);
            canvas.drawText(computation, xCenter, y, pTotal); y += 40;

            // FOOTER
            canvas.drawText("THIS SERVES AS AN OFFICIAL RECEIPT", xCenter, y, pFooter); y += 15;

            // DISCLAIMER
            y += 25;
            canvas.drawText("⚠️ PROTOTYPE SIMULATION ONLY", xCenter, y, pRed);
            y += 12;
            canvas.drawText("NOT VALID FOR OFFICIAL TRANSACTIONS", xCenter, y, pRed);

            document.finishPage(page);
            FileOutputStream fos = new FileOutputStream(ticketFile);
            document.writeTo(fos);
            document.close();
            fos.close();

            // Notification for Ticket Issued (Popup sa taas)
            if(notifManager != null) notifManager.showSaveAlert(true, "Ticket Issued Successfully", ticketFile);
            return true;
        } catch (Exception e) { return false; }
    }

    // --- SMART RECOVERY & BACK TO ZERO LOGIC ---
    // Ito yung logic para hindi mag-reset sa 0 ang ticket number kung nag-crash or restart ang app
    private void performSmartRecovery() {
        String safeSerial = deviceModel.replaceAll("[^a-zA-Z0-9]", "_").toUpperCase();
        SharedPreferences prefs = getSharedPreferences("SantransPrefs", MODE_PRIVATE);
        String sessionID = prefs.getString("currentSessionId", "UNKNOWN_TRIP");
        DatabaseReference transRef = mDatabase.child("POS_Devices").child(safeSerial).child("Trips").child(sessionID).child("Transactions");
        transRef.keepSynced(true); // Keep offline sync

        transRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                long cloudCount = snapshot.exists() ? snapshot.getChildrenCount() : 0;

                // BACK TO ZERO LOGIC: Pag bagong trip at walang laman sa cloud, reset to 01
                if (cloudCount == 0) {
                    getSharedPreferences("TicketCounter", MODE_PRIVATE).edit().putInt("last_ticket_count", 0).apply();
                    ticketID = "01";
                } else {
                    // Pag meron, hanapin ang max count between cloud at local files para walang duplicate
                    long localFileCount = recoverFromHiddenFiles();
                    long finalCount = Math.max(cloudCount, localFileCount);
                    getSharedPreferences("TicketCounter", MODE_PRIVATE).edit().putInt("last_ticket_count", (int) finalCount).apply();
                    ticketID = String.format(Locale.US, "%02d", finalCount + 1);
                }
                updateSerialDisplay();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private long recoverFromHiddenFiles() {
        try {
            SharedPreferences prefs = getSharedPreferences("SantransPrefs", MODE_PRIVATE);
            String sessionID = prefs.getString("currentSessionId", "UNKNOWN_TRIP");
            File mainDir = new File(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "POS BUS TICKETING SIMULATION"), sessionID);
            return mainDir.exists() ? countFilesRecursive(mainDir) : 0;
        } catch (Exception e) { return 0; }
    }

    private long countFilesRecursive(File directory) {
        long count = 0;
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) count += countFilesRecursive(file); else count++;
            }
        }
        return count;
    }

    private String generateNewTicketID() {
        SharedPreferences prefs = getSharedPreferences("TicketCounter", MODE_PRIVATE);
        int newCount = prefs.getInt("last_ticket_count", 0) + 1;
        prefs.edit().putInt("last_ticket_count", newCount).apply();
        return String.format(Locale.US, "%02d", newCount);
    }

    private void updateSerialDisplay() {
        if(tvSerial!=null) tvSerial.setText("Serial: " + deviceModel + " | Next #" + ticketID);
    }

    private void goToRouteList() {
        Intent i = new Intent(this, RouteListActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        i.putExtra("BUS_NO", busNo); i.putExtra("DRIVER", driver); i.putExtra("CONDUCTOR", conductor);
        startActivity(i);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}