package com.example.possantransbusticketingsystemapp;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    // --- UI COMPONENTS ---
    private EditText etBusNumber, etDriverName, etConductorName;
    private TextView tvHeaderBus, tvHeaderDriver, tvHeaderConductor, tvSerialNum, tvDateTime;
    private Button btnProceed, btnConductorMenu, btnUnlock;
    private ImageButton btnThemeToggleMain;

    // --- SYSTEM VARIABLES ---
    private static final String PREFS_NAME = "SantransPrefs";
    private SharedPreferences sharedPrefs;
    private BusNotificationManager busNotificationManager;

    private DatabaseReference deviceRef;
    private String deviceSerial;
    private String currentSessionId;

    private Handler timeHandler = new Handler();
    private Runnable timeRunnable;

    // --- NETWORK VARIABLES ---
    private TextView networkStatusBanner;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private Handler syncHandler = new Handler(Looper.getMainLooper());
    private boolean isCurrentlyOnline = false;
    private Runnable syncRunnable;

    private static final int PERMISSION_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply theme muna bago mag-load ang layout
        applyGlobalTheme();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            // Kunin ang Device ID para sa database path
            String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            String model = Build.MODEL.replaceAll("[^a-zA-Z0-9]", "_").toUpperCase();
            deviceSerial = model + "_" + androidId;
            deviceRef = FirebaseDatabase.getInstance().getReference("POS_Devices").child(deviceSerial);
            deviceRef.keepSynced(true);
        } catch (Exception e) {
            deviceSerial = "UNKNOWN_DEVICE";
        }

        sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        busNotificationManager = new BusNotificationManager(this);

        currentSessionId = sharedPrefs.getString("currentSessionId", null);
        if (currentSessionId == null) {
            createNewSessionID();
        }

        // Bind natin yung mga views galing XML
        etBusNumber = findViewById(R.id.etBusNumber);
        etDriverName = findViewById(R.id.etDriverName);
        etConductorName = findViewById(R.id.etConductorName);
        tvHeaderBus = findViewById(R.id.tvHeaderBus);
        tvHeaderDriver = findViewById(R.id.tvHeaderDriver);
        tvHeaderConductor = findViewById(R.id.tvHeaderConductor);
        tvSerialNum = findViewById(R.id.tvSerialNum);
        tvDateTime = findViewById(R.id.tvDateTime);
        btnProceed = findViewById(R.id.btnProceed);
        btnConductorMenu = findViewById(R.id.btnConductorMenu);
        btnUnlock = findViewById(R.id.btnUnlock);
        btnThemeToggleMain = findViewById(R.id.btnThemeToggleMain);

        if (tvSerialNum != null && deviceSerial != null) {
            tvSerialNum.setText("ID: " + deviceSerial.substring(0, Math.min(deviceSerial.length(), 15)) + "...");
        }

        if (savedInstanceState == null) {
            checkTermsAndConditions();
            checkAndRequestPermissions();

            // Check natin kung may data pa sa cloud na dapat i-restore
            boolean manuallyCleared = sharedPrefs.getBoolean("manually_cleared", false);
            if (!manuallyCleared) {
                checkRestoreStatus();
            } else {
                sharedPrefs.edit().putBoolean("manually_cleared", false).apply();
            }
        } else {
            loadLocalSessionData();
        }

        // Start na ng mga background tasks
        startRealTimeClock();
        setupRealTimeHeaderUpdates();
        updateThemeIcon();
        setupNetworkBanner();
        startNetworkMonitoring();
        startDataSyncTimer();

        if (btnThemeToggleMain != null) {
            btnThemeToggleMain.setOnClickListener(v -> showThemeChooserDialog());
        }

        btnProceed.setOnClickListener(v -> {
            String bus = etBusNumber.getText().toString().trim();
            String drv = etDriverName.getText().toString().trim();
            String con = etConductorName.getText().toString().trim();

            if (bus.isEmpty() || drv.isEmpty() || con.isEmpty()) {
                Toast.makeText(MainActivity.this, "Please fill in all information first!", Toast.LENGTH_SHORT).show();
            } else {
                saveSessionData(bus, drv, con,
                        getSharedPreferences("BusStats", MODE_PRIVATE).getInt("totalPax", 0),
                        getSharedPreferences("BusStats", MODE_PRIVATE).getFloat("cashAmount", 0.0f),
                        getSharedPreferences("BusStats", MODE_PRIVATE).getFloat("gcashAmount", 0.0f),
                        sharedPrefs.getString("lastTicketID", "0"),
                        sharedPrefs.getString("currentLoop", "N/A"));

                lockFields(true);

                Intent intent = new Intent(MainActivity.this, SelectionLoopActivity.class);
                intent.putExtra("BUS_NO", bus);
                intent.putExtra("DRIVER", drv);
                intent.putExtra("CONDUCTOR", con);
                intent.putExtra("DEVICE_ID", deviceSerial);
                intent.putExtra("SESSION_ID", currentSessionId);
                startActivity(intent);
            }
        });

        btnUnlock.setOnClickListener(v -> showPasscodeDialog(false));

        btnConductorMenu.setOnClickListener(v -> {
            Intent menuIntent = new Intent(MainActivity.this, ConductorMenuActivity.class);
            startActivity(menuIntent);
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

    }

    private void checkRestoreStatus() {
        String localBus = sharedPrefs.getString("busNumber", "");
        if (!localBus.isEmpty()) {
            loadLocalSessionData();
        } else {
            if (deviceRef != null) {
                deviceRef.child("LiveStatus").addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists() && snapshot.hasChild("busNumber")) {
                            String cloudBus = snapshot.child("busNumber").getValue(String.class);
                            String cloudDrv = snapshot.child("driver").getValue(String.class);
                            String cloudCon = snapshot.child("conductor").getValue(String.class);
                            String cloudId = snapshot.child("sessionId").getValue(String.class);
                            String cloudLoop = snapshot.child("currentLoop").getValue(String.class);
                            String cloudLastID = String.valueOf(snapshot.child("lastTicketID").getValue());

                            // 🟢 FIX: KINUHA KO NA RIN ANG REG/STU/SEN COUNTS
                            String sReg = String.valueOf(snapshot.child("regularCount").getValue());
                            String sStu = String.valueOf(snapshot.child("studentCount").getValue());
                            String sSen = String.valueOf(snapshot.child("seniorCount").getValue());

                            // Pass sa restoreSession
                            restoreSession(cloudBus, cloudDrv, cloudCon,
                                    String.valueOf(snapshot.child("totalPax").getValue()),
                                    sReg, sStu, sSen, // Passed new params
                                    String.valueOf(snapshot.child("totalCash").getValue()),
                                    String.valueOf(snapshot.child("totalGcash").getValue()),
                                    cloudLastID, cloudLoop, cloudId);
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
            }
        }
    }

    private void performClearData(String adminName) {
        if (deviceRef != null) deviceRef.child("LiveStatus").removeValue();

        try { BusDataBackup.deleteBackup(); } catch (Exception e) {}

        sharedPrefs.edit().clear().apply();
        sharedPrefs.edit().putBoolean("manually_cleared", true).apply();

        getSharedPreferences("BusStats", MODE_PRIVATE).edit().clear().apply();
        getSharedPreferences("TicketCounter", MODE_PRIVATE).edit().clear().apply();

        if(busNotificationManager != null) busNotificationManager.resetStats();

        createNewSessionID();

        etBusNumber.setText(""); etDriverName.setText(""); etConductorName.setText("");
        updateFields("--", "--", "--"); lockFields(false);

        Toast.makeText(this, "Session terminated and record cleared by " + adminName, Toast.LENGTH_LONG).show();
    }

    // 🟢 FIX: UPDATED RESTORE SESSION SIGNATURE & LOGIC
    private void restoreSession(String bus, String drv, String con, String sPax, String sReg, String sStu, String sSen, String sCash, String sGcash, String ticketID, String loop, String oldId) {
        try {
            int pax = 0; try { pax = Integer.parseInt(sPax); } catch (Exception e) {}

            // Parse individual counts
            int reg = 0; try { reg = Integer.parseInt(sReg); } catch (Exception e) {}
            int stu = 0; try { stu = Integer.parseInt(sStu); } catch (Exception e) {}
            int sen = 0; try { sen = Integer.parseInt(sSen); } catch (Exception e) {}

            float cash = 0f; try { cash = Float.parseFloat(sCash); } catch (Exception e) {}
            float gcash = 0f; try { gcash = Float.parseFloat(sGcash); } catch (Exception e) {}

            currentSessionId = oldId;
            sharedPrefs.edit().putString("currentSessionId", oldId).apply();

            getSharedPreferences("BusStats", MODE_PRIVATE).edit()
                    .putInt("totalPax", pax)
                    .putFloat("cashAmount", cash)
                    .putFloat("gcashAmount", gcash)
                    .putString("lastTicketID", ticketID)
                    .apply();

            saveSessionData(bus, drv, con, pax, cash, gcash, ticketID, loop);

            etBusNumber.setText(bus); etDriverName.setText(drv); etConductorName.setText(con);
            updateFields(bus, drv, con);
            lockFields(true);

            try {
                int lastID = Integer.parseInt(ticketID);
                getSharedPreferences("TicketCounter", MODE_PRIVATE).edit().putInt("last_ticket_count", lastID).apply();
            } catch (Exception e) { }

            // 🟢 FIX: PASS INDIVIDUAL COUNTS TO NOTIFICATION MANAGER
            if(busNotificationManager != null) {
                busNotificationManager.updateLiveStatus(loop, reg, stu, sen, cash, gcash, ticketID);
            }

        } catch (Exception e) { e.printStackTrace(); }
    }

    // --- MGA HELPER METHODS ---

    private void createNewSessionID() {
        currentSessionId = "TRIP_" + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date());
        sharedPrefs.edit().putString("currentSessionId", currentSessionId).apply();
    }

    private void lockFields(boolean isLocked) {
        etBusNumber.setEnabled(!isLocked); etDriverName.setEnabled(!isLocked); etConductorName.setEnabled(!isLocked);
        if (btnUnlock != null) btnUnlock.setVisibility(isLocked ? View.VISIBLE : View.GONE);
        if (btnProceed != null) {
            btnProceed.setText(isLocked ? "TRIP ACTIVE (LOCKED)" : "START TRIP");
            btnProceed.setBackgroundColor(isLocked ? 0xFF888888 : 0xFF0066CC);
        }
    }

    public void saveSessionData(String bus, String drv, String con, int pax, float cash, float gcash, String ticketID, String loop) {
        sharedPrefs.edit().putString("busNumber", bus).putString("driverName", drv).putString("conductorName", con).putString("lastTicketID", ticketID).putString("currentLoop", loop).putString("currentSessionId", currentSessionId).apply();
        if (deviceRef != null) {
            updateFirebaseNode(deviceRef.child("LiveStatus"), bus, drv, con, loop, ticketID, pax, cash, gcash);
            deviceRef.child("LiveStatus").child("sessionId").setValue(currentSessionId);
            updateFirebaseNode(deviceRef.child("Trips").child(currentSessionId), bus, drv, con, loop, ticketID, pax, cash, gcash);
        }
        try { BusDataBackup.saveFullBackup(bus, drv, con, pax, (int)cash, (int)gcash, ticketID, loop, currentSessionId); } catch (Exception e) {}
    }

    private void updateFirebaseNode(DatabaseReference ref, String bus, String drv, String con, String loop, String ticketID, int pax, float cash, float gcash) {
        ref.child("busNumber").setValue(bus); ref.child("driver").setValue(drv); ref.child("conductor").setValue(con);
        ref.child("currentLoop").setValue(loop); ref.child("lastTicketID").setValue(ticketID);
        ref.child("totalPax").setValue(pax); ref.child("totalCash").setValue(cash); ref.child("totalGcash").setValue(gcash);
        ref.child("lastUpdate").setValue(System.currentTimeMillis());
    }

    // 🟢 FIX: FETCH PASSCODE FROM FIREBASE
    private void showPasscodeDialog(boolean isForClearData) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(isForClearData ? "⚠️ ADMIN: TERMINATE TRIP" : "⚠️ ADMIN: UNLOCK FIELDS");
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(50, 40, 50, 10);
        final EditText inputCode = new EditText(this); inputCode.setHint("Admin Passcode");
        inputCode.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        layout.addView(inputCode);
        final EditText inputAdminName = new EditText(this);
        if (isForClearData) { inputAdminName.setHint("Inspector/Admin Name"); layout.addView(inputAdminName); }
        builder.setView(layout);
        builder.setPositiveButton("PROCEED", (dialog, which) -> {
            String input = inputCode.getText().toString();
            DatabaseReference passRef = FirebaseDatabase.getInstance().getReference("Config/GlobalSettings/adminPasscode");
            passRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    String serverPass = snapshot.getValue(String.class);
                    if (serverPass == null) serverPass = "1234"; // Default fallback

                    if (input.equals(serverPass)) {
                        if (isForClearData) performClearData(inputAdminName.getText().toString());
                        else lockFields(false);
                    } else {
                        Toast.makeText(MainActivity.this, "Access Denied.", Toast.LENGTH_SHORT).show();
                    }
                }
                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    // Fallback local check if offline
                    if (input.equals("1234")) {
                        if (isForClearData) performClearData(inputAdminName.getText().toString());
                        else lockFields(false);
                    } else {
                        Toast.makeText(MainActivity.this, "Offline: Use Default Code", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        });
        builder.setNegativeButton("CANCEL", null);
        builder.show();
    }

    private void applyGlobalTheme() {
        SharedPreferences themePrefs = getSharedPreferences("ThemePrefs", MODE_PRIVATE);
        int mode = themePrefs.getInt("mode", 0);
        int nightMode;
        switch (mode) { case 1: nightMode = AppCompatDelegate.MODE_NIGHT_NO; break; case 2: nightMode = AppCompatDelegate.MODE_NIGHT_YES; break; default: nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM; break; }
        AppCompatDelegate.setDefaultNightMode(nightMode);
    }

    private void showThemeChooserDialog() {
        String[] options = {"System Default", "Light Mode", "Dark Mode"};
        SharedPreferences themePrefs = getSharedPreferences("ThemePrefs", MODE_PRIVATE);
        int currentMode = themePrefs.getInt("mode", 0);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Appearance");
        builder.setSingleChoiceItems(options, currentMode, (dialog, which) -> {
            themePrefs.edit().putInt("mode", which).apply();
            switch (which) { case 1: AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO); break; case 2: AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES); break; default: AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM); break; }
            dialog.dismiss(); recreate();
        });
        builder.show();
    }

    private void updateThemeIcon() {
        if (btnThemeToggleMain == null) return;
        int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) { btnThemeToggleMain.setImageResource(R.drawable.ic_sun); } else { btnThemeToggleMain.setImageResource(R.drawable.ic_moon); }
    }

    private void checkTermsAndConditions() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (!prefs.getBoolean("is_terms_accepted", false)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            LayoutInflater inflater = this.getLayoutInflater();
            View dialogView = inflater.inflate(R.layout.dialog_terms, null);
            builder.setView(dialogView); builder.setCancelable(false);
            AlertDialog dialog = builder.create();
            CheckBox cbAgree = dialogView.findViewById(R.id.cbAgree);
            Button btnAccept = dialogView.findViewById(R.id.btnAcceptTerms);
            cbAgree.setOnCheckedChangeListener((buttonView, isChecked) -> { btnAccept.setEnabled(isChecked); btnAccept.setBackgroundTintList(ColorStateList.valueOf(isChecked ? Color.parseColor("#0066CC") : Color.LTGRAY)); });
            btnAccept.setOnClickListener(v -> { prefs.edit().putBoolean("is_terms_accepted", true).apply(); dialog.dismiss(); });
            dialog.show();
        }
    }

    private void checkAndRequestPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { if (!Environment.isExternalStorageManager()) { try { startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName()))); } catch (Exception e) {} } }
        else { if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE); }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) { permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION); permissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION); }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS); }
        if (!permissionsNeeded.isEmpty()) ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
    }

    private void setupNetworkBanner() {
        networkStatusBanner = new TextView(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.BOTTOM; networkStatusBanner.setLayoutParams(params); networkStatusBanner.setGravity(Gravity.CENTER); networkStatusBanner.setPadding(0, 20, 0, 20); networkStatusBanner.setTextColor(Color.WHITE); networkStatusBanner.setTextSize(13); networkStatusBanner.setTypeface(null, Typeface.BOLD); networkStatusBanner.setVisibility(View.GONE); networkStatusBanner.setElevation(100f);
        ViewGroup rootView = (ViewGroup) findViewById(android.R.id.content); if (rootView != null) rootView.addView(networkStatusBanner);
    }

    private void startNetworkMonitoring() {
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return;
        networkCallback = new ConnectivityManager.NetworkCallback() { @Override public void onAvailable(@NonNull Network network) { runOnUiThread(() -> { if (!isCurrentlyOnline) showOnlineBanner(); isCurrentlyOnline = true; }); } @Override public void onLost(@NonNull Network network) { runOnUiThread(() -> { isCurrentlyOnline = false; showOfflineBanner(); }); } };
        NetworkRequest networkRequest = new NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build();
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback);
        checkInitialNetworkStatus();
    }

    private void checkInitialNetworkStatus() {
        if (connectivityManager != null) {
            Network activeNetwork = connectivityManager.getActiveNetwork();
            NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(activeNetwork);
            isCurrentlyOnline = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            if (!isCurrentlyOnline) {
                showOfflineBanner();
            }
        }
    }

    private void showOfflineBanner() { if (networkStatusBanner != null) { networkStatusBanner.setText("No Internet Connection"); networkStatusBanner.setBackgroundColor(Color.parseColor("#333333")); networkStatusBanner.setVisibility(View.VISIBLE); } }
    private void showOnlineBanner() { if (networkStatusBanner != null) { networkStatusBanner.setText("Back Online"); networkStatusBanner.setBackgroundColor(Color.parseColor("#4CAF50")); networkStatusBanner.setVisibility(View.VISIBLE); new Handler(Looper.getMainLooper()).postDelayed(() -> { if (isCurrentlyOnline && networkStatusBanner != null) networkStatusBanner.setVisibility(View.GONE); }, 3000); } }
    private void startDataSyncTimer() { syncRunnable = new Runnable() { @Override public void run() { syncHandler.postDelayed(this, 1500); } }; syncHandler.post(syncRunnable); }
    private void startRealTimeClock() { timeRunnable = new Runnable() { @Override public void run() { if (tvDateTime != null) tvDateTime.setText("DATE & TIME: " + new SimpleDateFormat("MM/dd/yyyy hh:mm:ss a", Locale.getDefault()).format(new Date())); timeHandler.postDelayed(this, 1000); } }; timeHandler.post(timeRunnable); }
    private void setupRealTimeHeaderUpdates() { etBusNumber.addTextChangedListener(createWatcher(tvHeaderBus, "BUS: ")); etDriverName.addTextChangedListener(createWatcher(tvHeaderDriver, "DRV: ")); etConductorName.addTextChangedListener(createWatcher(tvHeaderConductor, "CON: ")); }
    private void loadLocalSessionData() { String bus = sharedPrefs.getString("busNumber", ""); if (!bus.isEmpty()) { etBusNumber.setText(bus); etDriverName.setText(sharedPrefs.getString("driverName", "")); etConductorName.setText(sharedPrefs.getString("conductorName", "")); updateFields(bus, etDriverName.getText().toString(), etConductorName.getText().toString()); lockFields(true); } }
    private void updateFields(String bus, String drv, String con) { tvHeaderBus.setText("BUS: " + bus); tvHeaderDriver.setText("DRV: " + drv); tvHeaderConductor.setText("CON: " + con); }
    private TextWatcher createWatcher(final TextView tv, final String prefix) { return new TextWatcher() { @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {} @Override public void onTextChanged(CharSequence s, int start, int before, int count) { tv.setText(prefix + s.toString()); } @Override public void afterTextChanged(Editable s) {} }; }

    @Override protected void onResume() { super.onResume(); updateThemeIcon(); }
    @Override protected void onDestroy() { super.onDestroy(); timeHandler.removeCallbacks(timeRunnable); syncHandler.removeCallbacks(syncRunnable); if (connectivityManager != null && networkCallback != null) { try { connectivityManager.unregisterNetworkCallback(networkCallback); } catch (Exception e) {} } }

}