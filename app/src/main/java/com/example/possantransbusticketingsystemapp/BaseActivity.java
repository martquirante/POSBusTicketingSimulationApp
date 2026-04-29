package com.example.possantransbusticketingsystemapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

public abstract class BaseActivity extends AppCompatActivity {

    // Network Variables
    private TextView networkStatusBanner;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean isCurrentlyOnline = false;

    // Sync Timer (1.5 Seconds)
    private Handler syncHandler = new Handler(Looper.getMainLooper());
    private Runnable syncRunnable;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // 1. DITO NATIN TINATAWAG ANG THEME PARA SA LAHAT NG NAG-EXTEND DITO
        applyGlobalTheme();
        super.onCreate(savedInstanceState);
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        setupNetworkMonitoring(); // Auto-start pag nag-load ang layout
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        setupNetworkMonitoring(); // Auto-start pag nag-load ang layout
    }

    // ==========================================
    //      THEME LOGIC (ITO YUNG HINAHANAP MO)
    // ==========================================
    private void applyGlobalTheme() {
        SharedPreferences themePrefs = getSharedPreferences("ThemePrefs", MODE_PRIVATE);
        int mode = themePrefs.getInt("mode", 0);
        int nightMode;
        switch (mode) {
            case 1: nightMode = AppCompatDelegate.MODE_NIGHT_NO; break;
            case 2: nightMode = AppCompatDelegate.MODE_NIGHT_YES; break;
            default: nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM; break;
        }
        AppCompatDelegate.setDefaultNightMode(nightMode);
    }

    // ==========================================
    //      NETWORK LOGIC (SPOTIFY STYLE)
    // ==========================================
    private void setupNetworkMonitoring() {
        // Create Banner Programmatically
        networkStatusBanner = new TextView(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.BOTTOM; // Sa baba lalabas

        networkStatusBanner.setLayoutParams(params);
        networkStatusBanner.setGravity(Gravity.CENTER);
        networkStatusBanner.setPadding(0, 20, 0, 20);
        networkStatusBanner.setTextColor(Color.WHITE);
        networkStatusBanner.setTextSize(13);
        networkStatusBanner.setTypeface(null, Typeface.BOLD);
        networkStatusBanner.setVisibility(View.GONE);
        networkStatusBanner.setElevation(100f); // Nasa ibabaw ng lahat

        // Add to Root View
        ViewGroup rootView = findViewById(android.R.id.content);
        if (rootView != null) {
            rootView.addView(networkStatusBanner);
        }

        startMonitoring();
        startSyncLoop();
    }

    private void startMonitoring() {
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager == null) return;

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                runOnUiThread(() -> {
                    if (!isCurrentlyOnline) {
                        showOnlineBanner(); // Green "Back Online"
                    }
                    isCurrentlyOnline = true;
                });
            }

            @Override
            public void onLost(@NonNull Network network) {
                runOnUiThread(() -> {
                    isCurrentlyOnline = false;
                    showOfflineBanner(); // Gray "No Internet"
                });
            }
        };

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        connectivityManager.registerNetworkCallback(request, networkCallback);

        // Initial Check
        checkInitialState();
    }

    private void checkInitialState() {
        if (connectivityManager != null) {
            Network activeNetwork = connectivityManager.getActiveNetwork();
            NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(activeNetwork);
            isCurrentlyOnline = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);

            if (!isCurrentlyOnline) {
                showOfflineBanner();
            }
        }
    }

    private void showOfflineBanner() {
        if (networkStatusBanner != null) {
            networkStatusBanner.setText("No Internet Connection");
            networkStatusBanner.setBackgroundColor(Color.parseColor("#333333")); // Dark Gray
            networkStatusBanner.setVisibility(View.VISIBLE);
        }
    }

    private void showOnlineBanner() {
        if (networkStatusBanner != null) {
            networkStatusBanner.setText("Back Online");
            networkStatusBanner.setBackgroundColor(Color.parseColor("#4CAF50")); // Green
            networkStatusBanner.setVisibility(View.VISIBLE);

            // Mawawala after 3 seconds
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (isCurrentlyOnline && networkStatusBanner != null) {
                    networkStatusBanner.setVisibility(View.GONE);
                }
            }, 3000);
        }
    }

    // ==========================================
    //      SYNC LOOP (1.5 SECONDS)
    // ==========================================
    private void startSyncLoop() {
        syncRunnable = new Runnable() {
            @Override
            public void run() {
                if (isCurrentlyOnline) {
                    onNetworkSync();
                }
                syncHandler.postDelayed(this, 1500); // 1.5 Seconds Interval
            }
        };
        syncHandler.post(syncRunnable);
    }

    // Override this in other activities if needed
    protected void onNetworkSync() {
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (connectivityManager != null && networkCallback != null) {
            try { connectivityManager.unregisterNetworkCallback(networkCallback); } catch (Exception e) {}
        }
        syncHandler.removeCallbacks(syncRunnable);
    }
}