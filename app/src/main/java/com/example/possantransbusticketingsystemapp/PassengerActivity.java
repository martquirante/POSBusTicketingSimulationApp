package com.example.possantransbusticketingsystemapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.button.MaterialButton;

public class PassengerActivity extends AppCompatActivity {

    // Mga variables
    private String busNo, driver, conductor, origin, destination;
    private String deviceId, sessionId; // ETO ANG BAGO: ID ng Device at Trip Session
    private double baseFare;
    private EditText etPassengerCount;

    // Header TextViews
    private TextView tvHeaderBus, tvHeaderSerial, tvRouteInfo, tvPriceInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // --- STEP 1: GLOBAL THEME APPLY ---
        applyGlobalTheme();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_passenger);

        // --- STEP 2: DATA RETRIEVAL (Kunin ang pinasa galing RouteList) ---
        Intent intent = getIntent();
        busNo = intent.getStringExtra("BUS_NO");
        driver = intent.getStringExtra("DRIVER");
        conductor = intent.getStringExtra("CONDUCTOR");
        origin = intent.getStringExtra("ORIGIN");
        destination = intent.getStringExtra("DESTINATION");
        baseFare = intent.getDoubleExtra("BASE_FARE", 0.0);

        // MAHALAGA: Kunin ang IDs para alam ng TicketActivity kung saang folder ilalagay
        deviceId = intent.getStringExtra("DEVICE_ID");
        sessionId = intent.getStringExtra("SESSION_ID");

        // --- STEP 3: VIEW BINDING ---
        tvHeaderBus = findViewById(R.id.tvHeaderBus);
        tvHeaderSerial = findViewById(R.id.tvHeaderSerial); // Siguraduhin na meron nito sa XML mo
        tvRouteInfo = findViewById(R.id.tvRouteInfo);
        tvPriceInfo = findViewById(R.id.tvPriceInfo);
        etPassengerCount = findViewById(R.id.etPassengerCount);

        MaterialButton btnRegular = findViewById(R.id.btnRegular);
        MaterialButton btnStudent = findViewById(R.id.btnStudent);
        MaterialButton btnSenior = findViewById(R.id.btnSenior);
        MaterialButton btnBack = findViewById(R.id.btnBackPassenger);

        // Display Info
        if (tvHeaderBus != null) tvHeaderBus.setText("BUS: " + (busNo != null ? busNo : "--"));

        // Display Serial (Kung wala sa intent, kunin sa Build Model)
        if (tvHeaderSerial != null) {
            String displaySerial = (deviceId != null) ? deviceId : android.os.Build.MODEL.toUpperCase();
            // Kung masyadong mahaba ang serial, putulin natin para kasya sa screen
            if (displaySerial.length() > 15) displaySerial = displaySerial.substring(0, 15) + "...";
            tvHeaderSerial.setText("ID: " + displaySerial);
        }

        if (tvRouteInfo != null) tvRouteInfo.setText(origin + " → " + destination);
        if (tvPriceInfo != null) tvPriceInfo.setText(String.format("Base Fare: ₱%.2f", baseFare));

        // --- STEP 4: BUTTON ACTIONS ---
        btnRegular.setOnClickListener(v -> proceedToTicket("Regular"));
        btnStudent.setOnClickListener(v -> proceedToTicket("Student"));
        btnSenior.setOnClickListener(v -> proceedToTicket("Senior/PWD"));

        btnBack.setOnClickListener(v -> finish());
    }

    private void proceedToTicket(String passengerType) {
        String countStr = etPassengerCount.getText().toString().trim();

        if (countStr.isEmpty() || countStr.equals("0")) {
            Toast.makeText(this, "Please enter number of passengers.", Toast.LENGTH_SHORT).show();
            return;
        }

        int count = Integer.parseInt(countStr);

        // --- CALCULATE DISCOUNT (20% for Student and Senior/PWD) ---
        // Pwede mo baguhin ang percentage dito kung magbago ang batas
        double discountPerPax = 0;
        if (passengerType.equals("Student") || passengerType.equals("Senior/PWD")) {
            discountPerPax = baseFare * 0.20;
        }

        double finalFarePerPax = baseFare - discountPerPax;
        double totalFare = finalFarePerPax * count;

        // --- IPASA ANG DATA SA TICKET SCREEN ---
        Intent intent = new Intent(PassengerActivity.this, TicketActivity.class);

        // Basic Info
        intent.putExtra("BUS_NO", busNo);
        intent.putExtra("DRIVER", driver);
        intent.putExtra("CONDUCTOR", conductor);
        intent.putExtra("ORIGIN", origin);
        intent.putExtra("DESTINATION", destination);

        // Transaction Info
        intent.putExtra("PASSENGER_TYPE", passengerType);
        intent.putExtra("PASSENGER_COUNT", count);
        intent.putExtra("BASE_FARE", baseFare);
        intent.putExtra("DISCOUNT_AMOUNT", discountPerPax);
        intent.putExtra("TOTAL_AMOUNT", totalFare);

        // *** IMPORTANTE: Ipasa ang Keys para sa Database ***
        intent.putExtra("DEVICE_ID", deviceId);
        intent.putExtra("SESSION_ID", sessionId);

        startActivity(intent);
    }

    private void applyGlobalTheme() {
        SharedPreferences themePrefs = getSharedPreferences("ThemePrefs", MODE_PRIVATE);
        int mode = themePrefs.getInt("mode", 0);
        switch (mode) {
            case 0: AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM); break;
            case 1: AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO); break;
            case 2: AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES); break;
        }
    }
}