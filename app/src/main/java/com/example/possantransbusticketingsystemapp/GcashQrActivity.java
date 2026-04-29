package com.example.possantransbusticketingsystemapp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class GcashQrActivity extends AppCompatActivity {

    private String busNo, driver, conductor, origin, destination, passengerType;
    private int passengerCount;
    private double baseFare, discountAmount, totalAmount, finalPricePerPax;
    private String deviceModel;

    private String ticketID;
    private DatabaseReference mDatabase;
    private BusNotificationManager notifManager;

    // UI Elements
    private EditText etReferenceNumber;
    private TextView tvTotalAmountQr;
    private Button btnConfirm, btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gcash_qr); // Make sure this matches your XML filename

        try {
            // Offline Persistence
            try {
                if (FirebaseDatabase.getInstance() != null) {
                    FirebaseDatabase.getInstance().setPersistenceEnabled(true);
                }
            } catch (Exception e) {
                // Ignore if already set
            }

            mDatabase = FirebaseDatabase.getInstance().getReference();
            // notifManager = new BusNotificationManager(this); // Uncomment if class exists
            deviceModel = android.os.Build.MODEL.toUpperCase();

            // 1. Get Data from TicketActivity
            Intent intent = getIntent();
            if (intent != null) {
                busNo = intent.getStringExtra("BUS_NO");
                driver = intent.getStringExtra("DRIVER");
                conductor = intent.getStringExtra("CONDUCTOR");
                origin = intent.getStringExtra("ORIGIN");
                destination = intent.getStringExtra("DESTINATION");
                passengerType = intent.getStringExtra("PASSENGER_TYPE");
                passengerCount = intent.getIntExtra("PASSENGER_COUNT", 1);
                baseFare = intent.getDoubleExtra("BASE_FARE", 0.0);
                discountAmount = intent.getDoubleExtra("DISCOUNT_AMOUNT", 0.0);
                finalPricePerPax = intent.getDoubleExtra("FINAL_PRICE_PER_PAX", 0.0);

                // IMPORTANT: Check this Key matches TicketActivity exactly
                totalAmount = intent.getDoubleExtra("TOTAL_AMOUNT", 0.0);

                // DEBUG: Check kung may nareceive na amount
                // Toast.makeText(this, "DEBUG: Total Amount is " + totalAmount, Toast.LENGTH_LONG).show();
            }

            // 2. Bind UI Elements (IDs matched to your XML)
            etReferenceNumber = findViewById(R.id.etReferenceNumber);
            tvTotalAmountQr = findViewById(R.id.tvTotalAmountQr); // Correct ID from XML
            btnConfirm = findViewById(R.id.btnConfirmPayment);    // Correct ID from XML
            btnBack = findViewById(R.id.btnCancelQr);             // Correct ID from XML

            // Display Amount
            if (tvTotalAmountQr != null) {
                tvTotalAmountQr.setText("₱" + String.format("%.2f", totalAmount));
            }

            // 3. Confirm Button Logic
            if (btnConfirm != null) {
                btnConfirm.setOnClickListener(v -> {
                    // DEBUG: Check if button is working
                    // Toast.makeText(this, "Processing...", Toast.LENGTH_SHORT).show();

                    String refNum = "";
                    if (etReferenceNumber != null) {
                        refNum = etReferenceNumber.getText().toString().trim();
                    }

                    // Format Payment String
                    String paymentMethodString = "GCASH";
                    if (!refNum.isEmpty()) {
                        paymentMethodString += " (Ref: " + refNum + ")";
                    }

                    // Generate ID
                    ticketID = generateTicketID();

                    // Save PDF & Firebase
                    boolean isSaved = saveTicketToHiddenPDF(paymentMethodString);

                    if (isSaved) {
                        saveToFirebase(paymentMethodString);
                    } else {
                        // If PDF fails, still try to save to Firebase but warn user
                        saveToFirebase(paymentMethodString);
                        Toast.makeText(this, "Warning: Receipt PDF not saved.", Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                Toast.makeText(this, "Error: Confirm Button ID not found!", Toast.LENGTH_LONG).show();
            }

            // 4. Cancel Button Logic
            if (btnBack != null) {
                btnBack.setOnClickListener(v -> {
                    finish(); // Close activity
                });
            } else {
                Toast.makeText(this, "Error: Cancel Button ID not found!", Toast.LENGTH_LONG).show();
            }

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error initializing: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String generateTicketID() {
        SharedPreferences prefs = getSharedPreferences("TicketCounter", MODE_PRIVATE);
        int lastCount = prefs.getInt("last_ticket_count", 0);
        int newCount = lastCount + 1;
        prefs.edit().putInt("last_ticket_count", newCount).apply();
        return String.format(Locale.US, "%02d", newCount);
    }

    private void saveToFirebase(String paymentMethodString) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            String currentTimestamp = sdf.format(new Date());

            double lat = 0.0;
            double lng = 0.0;
            String locName = "GCash Payment";

            // Ensure TicketTransaction.java has the matching constructor!
            TicketTransaction transaction = new TicketTransaction(
                    busNo, driver, conductor, origin, destination,
                    passengerType, passengerCount, baseFare, discountAmount, totalAmount,
                    paymentMethodString,
                    currentTimestamp, deviceModel, ticketID,
                    lat, lng, locName
            );

            String safeSerial = deviceModel.replaceAll("[^a-zA-Z0-9]", "_").toUpperCase();
            SharedPreferences prefs = getSharedPreferences("SantransPrefs", MODE_PRIVATE);
            String sessionID = prefs.getString("currentSessionId", "UNKNOWN_TRIP");
            SharedPreferences stats = getSharedPreferences("BusStats", Context.MODE_PRIVATE);

            // Update Stats Locally
            int currentPax = stats.getInt("totalPax", 0);
            float currentCash = stats.getFloat("cashAmount", 0.0f);
            float currentGcash = stats.getFloat("gcashAmount", 0.0f);

            currentPax += passengerCount;
            currentGcash += (float) totalAmount;

            int reg = stats.getInt("countRegular", 0);
            int stu = stats.getInt("countStudent", 0);
            int sen = stats.getInt("countSenior", 0);

            if (passengerType != null) {
                if (passengerType.equalsIgnoreCase("Student")) stu += passengerCount;
                else if (passengerType.contains("Senior") || passengerType.contains("PWD")) sen += passengerCount;
                else reg += passengerCount;
            } else {
                reg += passengerCount;
            }

            SharedPreferences.Editor editor = stats.edit();
            editor.putInt("totalPax", currentPax);
            editor.putFloat("cashAmount", currentCash);
            editor.putFloat("gcashAmount", currentGcash);
            editor.putInt("countRegular", reg);
            editor.putInt("countStudent", stu);
            editor.putInt("countSenior", sen);
            editor.apply();

            // Firebase Paths
            String basePath = "/POS_Devices/" + safeSerial;
            String tripPath = basePath + "/Trips/" + sessionID;
            String livePath = basePath + "/LiveStatus";

            Map<String, Object> allUpdates = new HashMap<>();

            allUpdates.put(tripPath + "/Transactions/Ticket_" + ticketID, transaction);

            allUpdates.put(livePath + "/totalPax", currentPax);
            allUpdates.put(livePath + "/totalCash", currentCash);
            allUpdates.put(livePath + "/totalGcash", currentGcash);
            allUpdates.put(livePath + "/lastTicketID", ticketID);
            allUpdates.put(livePath + "/lastUpdate", System.currentTimeMillis());

            allUpdates.put(livePath + "/regularCount", reg);
            allUpdates.put(livePath + "/studentCount", stu);
            allUpdates.put(livePath + "/seniorCount", sen);

            allUpdates.put(tripPath + "/totalPax", currentPax);
            allUpdates.put(tripPath + "/totalCash", currentCash);
            allUpdates.put(tripPath + "/totalGcash", currentGcash);

            mDatabase.updateChildren(allUpdates, (error, ref) -> {
                if (error == null) {
                    Toast.makeText(this, "Payment Confirmed! Ticket #" + ticketID, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "Saved Offline. Ticket #" + ticketID, Toast.LENGTH_LONG).show();
                }

                Intent intentReturn = new Intent(GcashQrActivity.this, RouteListActivity.class);
                intentReturn.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                intentReturn.putExtra("BUS_NO", busNo);
                intentReturn.putExtra("DRIVER", driver);
                intentReturn.putExtra("CONDUCTOR", conductor);
                startActivity(intentReturn);
                finish();
            });

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error saving to database: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private boolean saveTicketToHiddenPDF(String paymentMethodString) {
        try {
            SharedPreferences prefs = getSharedPreferences("SantransPrefs", MODE_PRIVATE);
            String sessionID = prefs.getString("currentSessionId", "UNKNOWN_TRIP");

            File docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            File appDir = new File(docsDir, "SANTRANS_POS_FILES");
            File ticketDir = new File(appDir, "CUSTOMER_TICKET");
            File sessionDir = new File(ticketDir, sessionID);

            if (!sessionDir.exists()) sessionDir.mkdirs();

            SimpleDateFormat sdfReceipt = new SimpleDateFormat("MM/dd/yyyy hh:mm a", Locale.US);
            Date now = new Date();

            String filename = "TICKET_" + ticketID + "_GCASH.pdf";
            File ticketFile = new File(sessionDir, filename);

            PdfDocument document = new PdfDocument();
            int pageWidth = 300;
            int blockHeight = 250;
            int headerFooterBuffer = 300;
            int pageHeight = headerFooterBuffer + (blockHeight * passengerCount);

            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create();
            PdfDocument.Page page = document.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            Paint paintCenter = new Paint();
            paintCenter.setColor(Color.BLACK);
            paintCenter.setTextSize(14);
            paintCenter.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
            paintCenter.setTextAlign(Paint.Align.CENTER);

            Paint paintLeft = new Paint();
            paintLeft.setColor(Color.BLACK);
            paintLeft.setTextSize(12);
            paintLeft.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));
            paintLeft.setTextAlign(Paint.Align.LEFT);

            Paint paintBold = new Paint();
            paintBold.setColor(Color.BLACK);
            paintBold.setTextSize(12);
            paintBold.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paintBold.setTextAlign(Paint.Align.LEFT);

            int y = 30;
            int xCenter = pageWidth / 2;
            int xLeft = 20;

            for (int i = 0; i < passengerCount; i++) {
                canvas.drawText("SANTRANS CORPORATION", xCenter, y, paintCenter); y += 20;
                canvas.drawText("Serial#: " + deviceModel + " BUS#: " + busNo, xCenter, y, paintCenter); y += 20;
                canvas.drawText("OFFICIAL RECEIPT (GCASH)", xCenter, y, paintCenter); y += 30;

                canvas.drawText("Driver: " + driver, xLeft, y, paintLeft); y += 15;
                canvas.drawText("Conductor: " + conductor, xLeft, y, paintLeft); y += 15;
                canvas.drawText("DATE: " + sdfReceipt.format(now), xLeft, y, paintLeft); y += 15;
                canvas.drawText("From: " + origin, xLeft, y, paintLeft); y += 15;
                canvas.drawText("To: " + destination, xLeft, y, paintLeft); y += 15;

                canvas.drawText("Type: " + passengerType, xLeft, y, paintLeft); y += 15;
                canvas.drawText("Fare: ₱" + String.format("%.2f", baseFare), xLeft, y, paintLeft); y += 15;

                String discText = (discountAmount > 0) ? String.format("₱%.2f", discountAmount) : "None";
                canvas.drawText("Disc: " + discText, xLeft, y, paintLeft); y += 20;

                canvas.drawText("PAYMENT: " + paymentMethodString, xLeft, y, paintLeft); y += 20;

                canvas.drawText("AMOUNT: ₱" + String.format("%.2f", finalPricePerPax), xLeft, y, paintBold); y += 20;

                canvas.drawText("Ref Ticket ID: " + ticketID, xLeft, y, paintLeft); y += 40;
            }

            canvas.drawText("TOTAL PAID: ₱" + String.format("%.2f", totalAmount), xCenter, y, paintCenter); y += 30;

            Paint paintItalic = new Paint();
            paintItalic.setColor(Color.DKGRAY);
            paintItalic.setTextSize(10);
            paintItalic.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC));
            paintItalic.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("This serves as an official receipt.", xCenter, y, paintItalic);

            document.finishPage(page);

            FileOutputStream fos = new FileOutputStream(ticketFile);
            document.writeTo(fos);
            document.close();
            fos.close();

            // if (notifManager != null) { ... } // Uncomment if needed

            return true;

        } catch (IOException e) {
            e.printStackTrace();
            showRedError("PDF Save Failed: " + e.getMessage());
            return false;
        }
    }

    private void showRedError(String message) {
        View rootView = findViewById(android.R.id.content);
        if (rootView != null) {
            Snackbar snackbar = Snackbar.make(rootView, message, Snackbar.LENGTH_LONG);
            snackbar.setBackgroundTint(Color.RED);
            snackbar.setTextColor(Color.WHITE);
            snackbar.show();
        } else {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
    }
}