package com.example.possantransbusticketingsystemapp;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class RouteListActivity extends BaseActivity {

    // Mga variables para sa info ng bus at listahan ng ruta
    private String busNo, driver, conductor;
    private boolean isReverse;
    private LinearLayout routesContainer;
    private DatabaseReference databaseRef;

    private ValueEventListener routeListener;
    private BusNotificationManager notifManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_route_list);

        // 1. SETUP NOTIFICATION MANAGER
        notifManager = new BusNotificationManager(this);

        // 2. CHECK PERMISSION (Para sa Android 13 pataas)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        // 3. SALUHIN ANG DATA
        Intent intent = getIntent();
        if (intent != null) {
            busNo = intent.getStringExtra("BUS_NO");
            driver = intent.getStringExtra("DRIVER");
            conductor = intent.getStringExtra("CONDUCTOR");
            isReverse = intent.getBooleanExtra("IS_REVERSE", false);
        } else {
            SharedPreferences prefs = getSharedPreferences("SantransPrefs", MODE_PRIVATE);
            busNo = prefs.getString("busNumber", "--");
            driver = prefs.getString("driverName", "--");
            conductor = prefs.getString("conductorName", "--");
            isReverse = false;
        }

        // 4. UPDATE NOTIFICATION HEADER (Fixed logic)
        updateNotificationDirection();

        routesContainer = findViewById(R.id.routesContainer);

        // 5. INDICATOR LABEL
        TextView tvLoopDirection = findViewById(R.id.tvLoopDirection);
        if (tvLoopDirection != null) {
            String loopStatus = isReverse ? "REVERSE LOOP: ST.CRUZ → FVR" : "FORWARD LOOP: FVR → ST.CRUZ";
            tvLoopDirection.setText(loopStatus);

            if (isReverse) {
                tvLoopDirection.setTextColor(Color.parseColor("#D32F2F")); // Red
                tvLoopDirection.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FFEBEE")));
            } else {
                tvLoopDirection.setTextColor(ContextCompat.getColor(this, R.color.brand_primary)); // Blue
                tvLoopDirection.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E1F5FE")));
            }
        }

        // 6. MENU AT BACK BUTTONS
        MaterialButton btnConductorMenu = findViewById(R.id.btnConductorMenu);
        MaterialButton btnBack = findViewById(R.id.btnBack);

        if (btnConductorMenu != null) {
            btnConductorMenu.setOnClickListener(v -> {
                Intent menuIntent = new Intent(RouteListActivity.this, ConductorMenuActivity.class);
                startActivity(menuIntent);
            });
        }

        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        // 7. KONEKTA SA FIREBASE (With Offline Capability)
        String path = isReverse ? "Routes_Reverse" : "Routes_Forward";
        databaseRef = FirebaseDatabase.getInstance().getReference(path);
        databaseRef.keepSynced(true);

        addHeader("Loading Routes...");

        // Setup Listener
        routeListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (routesContainer != null) {
                    routesContainer.removeAllViews();
                }

                if (!snapshot.exists()) {
                    addHeader("No routes found.");
                    return;
                }

                String lastOrigin = "";
                for (DataSnapshot routeSnap : snapshot.getChildren()) {
                    Route route = routeSnap.getValue(Route.class);
                    if (route != null) {
                        if (!route.origin.equals(lastOrigin)) {
                            addHeader("FROM " + route.origin.toUpperCase() + ":");
                            lastOrigin = route.origin;
                        }
                        addRoute(route.origin, route.destination, route.price);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(RouteListActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };

        // Attach Listener
        databaseRef.addValueEventListener(routeListener);
    }

    // =================================================================
    //  HELPER: Update Notification Direction (FIXED: 7 Parameters)
    // =================================================================
    private void updateNotificationDirection() {
        // 1. Kunin ang saved stats mula sa SharedPreferences
        SharedPreferences stats = getSharedPreferences("BusStats", Context.MODE_PRIVATE);

        // 🟢 FIX: Kunin ang individual counts para ipasa sa Manager
        // Note: Kailangan siguraduhin na nasesave ang mga ito sa TicketActivity
        // Kung wala pang saved data, 0 ang default.
        int savedReg = stats.getInt("countRegular", 0);
        int savedStu = stats.getInt("countStudent", 0);
        int savedSen = stats.getInt("countSenior", 0);

        float savedCash = stats.getFloat("cashAmount", 0f);
        float savedGcash = stats.getFloat("gcashAmount", 0f);
        String savedLastTix = stats.getString("lastTicketID", "--");

        // 2. Tukuyin ang tamang Loop Name
        String loopNameForNotif = isReverse ? "ST.CRUZ Terminal" : "FVR Terminal";

        // 3. Update ang notification (FIXED: 7 Parameters na ito)
        if (notifManager != null) {
            notifManager.updateLiveStatus(
                    loopNameForNotif,
                    savedReg,   // Regular
                    savedStu,   // Student
                    savedSen,   // Senior
                    savedCash,
                    savedGcash,
                    savedLastTix
            );
        }
    }

    // Helper: Headers
    private void addHeader(String title) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextSize(22);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(ContextCompat.getColor(this, R.color.brand_primary));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 45, 0, 20);
        tv.setLayoutParams(params);
        tv.setPadding(15, 0, 0, 0);

        routesContainer.addView(tv);
    }

    // Helper: Buttons
    private void addRoute(String origin, String destination, double price) {
        MaterialButton btn = new MaterialButton(this);
        btn.setText("FROM " + origin + " → " + destination);
        btn.setTextSize(20);
        btn.setTextColor(Color.WHITE);
        btn.setTypeface(null, Typeface.BOLD);
        btn.setAllCaps(true);
        btn.setCornerRadius(20);

        int brandColor = ContextCompat.getColor(this, R.color.brand_primary);
        btn.setBackgroundTintList(ColorStateList.valueOf(brandColor));

        btn.setPadding(30, 40, 30, 40);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 15, 0, 25);
        btn.setLayoutParams(params);

        btn.setOnClickListener(v -> {
            Intent intent = new Intent(RouteListActivity.this, PassengerActivity.class);
            intent.putExtra("BUS_NO", busNo);
            intent.putExtra("DRIVER", driver);
            intent.putExtra("CONDUCTOR", conductor);
            intent.putExtra("ORIGIN", origin);
            intent.putExtra("DESTINATION", destination);
            intent.putExtra("BASE_FARE", price);
            startActivity(intent);
        });

        routesContainer.addView(btn);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (databaseRef != null && routeListener != null) {
            databaseRef.removeEventListener(routeListener);
        }
    }
}