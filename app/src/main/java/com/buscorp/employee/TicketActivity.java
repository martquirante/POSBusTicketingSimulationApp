package com.buscorp.employee;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.airbnb.lottie.LottieAnimationView;
import com.buscorp.employee.core.db.AppDatabase;
import com.buscorp.employee.core.db.TicketDao;
import com.buscorp.employee.core.db.TicketEntity;
import com.buscorp.employee.core.sync.OfflineSyncWorker;
import com.google.android.material.button.MaterialButton;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TicketActivity extends AppCompatActivity {

    private ImageView btnBack;
    private AutoCompleteTextView spinnerOrigin, spinnerDestination;
    private LinearLayout btnTypeRegular, btnTypeStudent, btnTypeSenior, btnTypePwd;
    private TextView tvCalculatedFare;
    private MaterialButton btnIssue;
    private View overlaySuccess;
    private LottieAnimationView lottieSuccess;

    private String selectedPassengerType = "Regular";
    private double baseFare = 15.0; // Minimal dummy logic for simulation
    private double currentCalculatedFare = 15.0;

    // Database
    private TicketDao ticketDao;
    private ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(R.style.Theme_BusCorp_DayNight_NoActionBar);
        setContentView(R.layout.activity_ticket);

        ticketDao = AppDatabase.getInstance(this).ticketDao();

        initViews();
        setupDropdowns();
        setupPassengerTypeSelection();
        setupClickListeners();
        
        updateFareDisplay();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        spinnerOrigin = findViewById(R.id.spinnerOrigin);
        spinnerDestination = findViewById(R.id.spinnerDestination);
        
        btnTypeRegular = findViewById(R.id.btnTypeRegular);
        btnTypeStudent = findViewById(R.id.btnTypeStudent);
        btnTypeSenior = findViewById(R.id.btnTypeSenior);
        btnTypePwd = findViewById(R.id.btnTypePwd);
        
        tvCalculatedFare = findViewById(R.id.tvCalculatedFare);
        btnIssue = findViewById(R.id.btnIssue);
        
        overlaySuccess = findViewById(R.id.overlaySuccess);
        lottieSuccess = findViewById(R.id.lottieSuccess);
    }

    private void setupDropdowns() {
        String[] stops = new String[]{"Terminal A", "City Center", "North Avenue", "South Park", "Terminal B"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, stops);
        
        spinnerOrigin.setAdapter(adapter);
        spinnerDestination.setAdapter(adapter);
        
        // Listeners to recalculate fare when route changes
        spinnerOrigin.setOnItemClickListener((parent, view, position, id) -> recalculateFare());
        spinnerDestination.setOnItemClickListener((parent, view, position, id) -> recalculateFare());
    }

    private void setupPassengerTypeSelection() {
        View.OnClickListener typeListener = v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            
            // Reset all to inactive
            resetTypeButton(btnTypeRegular, R.drawable.ic_person_tie);
            resetTypeButton(btnTypeStudent, R.drawable.ic_school);
            resetTypeButton(btnTypeSenior, R.drawable.ic_elderly);
            resetTypeButton(btnTypePwd, R.drawable.ic_elderly);
            
            // Set clicked to active
            int id = v.getId();
            if (id == R.id.btnTypeRegular) {
                selectedPassengerType = "Regular";
                setActiveTypeButton(btnTypeRegular, R.drawable.ic_person_tie);
            } else if (id == R.id.btnTypeStudent) {
                selectedPassengerType = "Student";
                setActiveTypeButton(btnTypeStudent, R.drawable.ic_school);
            } else if (id == R.id.btnTypeSenior) {
                selectedPassengerType = "Senior";
                setActiveTypeButton(btnTypeSenior, R.drawable.ic_elderly);
            } else if (id == R.id.btnTypePwd) {
                selectedPassengerType = "PWD";
                setActiveTypeButton(btnTypePwd, R.drawable.ic_elderly);
            }
            
            recalculateFare();
        };

        btnTypeRegular.setOnClickListener(typeListener);
        btnTypeStudent.setOnClickListener(typeListener);
        btnTypeSenior.setOnClickListener(typeListener);
        btnTypePwd.setOnClickListener(typeListener);
    }

    private void resetTypeButton(LinearLayout btn, int iconRes) {
        btn.setBackgroundResource(R.drawable.bg_passenger_type_inactive);
        ImageView icon = (ImageView) btn.getChildAt(0);
        TextView text = (TextView) btn.getChildAt(1);
        icon.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary));
        text.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
    }

    private void setActiveTypeButton(LinearLayout btn, int iconRes) {
        btn.setBackgroundResource(R.drawable.bg_passenger_type_active);
        ImageView icon = (ImageView) btn.getChildAt(0);
        TextView text = (TextView) btn.getChildAt(1);
        icon.setColorFilter(ContextCompat.getColor(this, R.color.buscorp_cyan));
        text.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
    }

    private void recalculateFare() {
        String origin = spinnerOrigin.getText().toString();
        String dest = spinnerDestination.getText().toString();
        
        // Dummy logic: Base 15 + distance simulated if both selected
        double fare = baseFare;
        if (!origin.isEmpty() && !dest.isEmpty() && !origin.equals(dest)) {
            fare = 25.0; // Simulated distant fare
        }
        
        // Apply 20% discount
        if (!selectedPassengerType.equals("Regular")) {
            fare = fare * 0.8;
        }
        
        currentCalculatedFare = fare;
        updateFareDisplay();
    }

    private void updateFareDisplay() {
        tvCalculatedFare.setText(String.format("₱%.2f", currentCalculatedFare));
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> finish());
        
        btnIssue.setOnClickListener(v -> {
            String origin = spinnerOrigin.getText().toString();
            String dest = spinnerDestination.getText().toString();
            
            if (origin.isEmpty() || dest.isEmpty()) {
                Toast.makeText(this, "Please select origin and destination.", Toast.LENGTH_SHORT).show();
                return;
            }
            
            v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            issueTicket(origin, dest);
        });
    }

    private void issueTicket(String origin, String dest) {
        TicketEntity ticket = new TicketEntity(
                origin,
                dest,
                currentCalculatedFare,
                selectedPassengerType,
                System.currentTimeMillis(),
                false // isSynced
        );

        // Save to Room DB asynchronously
        dbExecutor.execute(() -> {
            ticketDao.insert(ticket);
            
            // Queue WorkManager sync
            enqueueSyncWorker();
            
            // Show success overlay on UI thread
            new Handler(Looper.getMainLooper()).post(this::showSuccessOverlay);
        });
    }

    private void enqueueSyncWorker() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
                
        OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(OfflineSyncWorker.class)
                .setConstraints(constraints)
                .build();
                
        WorkManager.getInstance(this).enqueue(syncRequest);
    }

    private void showSuccessOverlay() {
        overlaySuccess.setAlpha(0f);
        overlaySuccess.setVisibility(View.VISIBLE);
        overlaySuccess.animate().alpha(1f).setDuration(300).start();
        
        lottieSuccess.playAnimation();
        
        // Hide overlay and reset form after delay
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            overlaySuccess.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                overlaySuccess.setVisibility(View.GONE);
                spinnerOrigin.setText("");
                spinnerDestination.setText("");
                btnTypeRegular.performClick(); // Reset to Regular
            }).start();
        }, 2000);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbExecutor != null && !dbExecutor.isShutdown()) {
            dbExecutor.shutdown();
        }
        if (lottieSuccess != null) {
            lottieSuccess.removeAllAnimatorListeners();
            lottieSuccess.cancelAnimation();
        }
    }
}