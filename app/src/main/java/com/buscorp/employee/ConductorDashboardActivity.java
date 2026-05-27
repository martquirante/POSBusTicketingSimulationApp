package com.buscorp.employee;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import dagger.hilt.android.AndroidEntryPoint;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.buscorp.employee.databinding.ActivityConductorDashboardBinding;
import com.buscorp.employee.core.sync.OfflineSyncWorker;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import timber.log.Timber;

/**
 * ConductorDashboardActivity
 *
 * ┌─────────────────────────────────────────────────────────┐
 * │  HYBRID DATABASE ARCHITECTURE (CRITICAL — DO NOT CHANGE) │
 * │                                                         │
 * │  Firebase RTDB  →  Live GPS, AlightRequests, LiveStatus │
 * │    Path: POS_Devices/{serial}/AlightRequests            │
 * │    Path: POS_Devices/{serial}/LiveStatus                │
 * │                                                         │
 * │  Supabase REST  →  Tickets, History, Auth, Remittances  │
 * └─────────────────────────────────────────────────────────┘
 *
 * The Admin Command Center (Next.js) reads ONLY from Firebase RTDB
 * for live fleet data. Any GPS or live stats written to Supabase will
 * NOT appear on the Admin live map. Always write live data to RTDB.
 */
@AndroidEntryPoint
public class ConductorDashboardActivity extends AppCompatActivity implements SensorEventListener {

    private static final String TAG = "ConductorDashboard";

    // ── Firebase RTDB paths (must match Admin's firebasePaths constants) ──
    // firebasePaths.posDevices = "POS_Devices"
    // Device serial is stored in SharedPreferences as "deviceSerial"
    private static final String RTDB_ROOT_DEVICES = "POS_Devices";
    private static final String RTDB_NODE_ALIGHT   = "AlightRequests";
    private static final String RTDB_NODE_LIVE     = "LiveStatus";

    // ── View Binding ──
    private ActivityConductorDashboardBinding binding;
    private DashboardViewModel viewModel;

    // ── Firebase ──
    private DatabaseReference alightRef;
    private DatabaseReference liveStatusRef;
    private ValueEventListener alightListener;
    private ValueEventListener liveStatusListener;

    // ── Gyroscope / Parallax ──
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private static final float MAX_TILT_DEG = 6f;
    private static final float TILT_ALPHA   = 0.08f;  // Low-pass filter weight
    private float smoothedX = 0f;
    private float smoothedY = 0f;

    // ── Device serial (used as Firebase RTDB key) ──
    private String deviceSerial;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityConductorDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(DashboardViewModel.class);

        resolveDeviceSerial();
        setupGreeting();
        setupAnimations();
        setupClickListeners();
        setupGyroParallax();
        observeSyncStatus();

        // ────────────────────────────────────────────────────────────
        // FIREBASE RTDB listeners — both AlightRequests & LiveStatus
        // These are the ONLY live-data sources. Supabase is NOT used here.
        // ────────────────────────────────────────────────────────────
        attachFirebaseAlightListener();
        attachFirebaseLiveStatusListener();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION 1 — FIREBASE RTDB: ALIGHT NOTIFICATIONS
    // Path: POS_Devices/{serial}/AlightRequests
    //
    // Admin Command Center writes to this path when a passenger sends an
    // alight request via the Passenger App. The conductor sees it here in
    // real-time so they can prepare to stop.
    // ─────────────────────────────────────────────────────────────────────────
    private void attachFirebaseAlightListener() {
        alightRef = FirebaseDatabase.getInstance()
                .getReference(RTDB_ROOT_DEVICES)
                .child(deviceSerial)
                .child(RTDB_NODE_ALIGHT);

        alightListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists() || !snapshot.hasChildren()) {
                    // No pending alight requests
                    binding.tvAlightMessage.setText("Monitoring passenger stops…");
                    binding.tvAlightMessage.setTextColor(0xFF9CA3AF);
                    return;
                }

                // Count pending requests and build display message
                long pendingCount = 0;
                String nearestStop = null;

                for (DataSnapshot request : snapshot.getChildren()) {
                    String status = request.child("status").getValue(String.class);
                    if ("pending".equals(status)) {
                        pendingCount++;
                        if (nearestStop == null) {
                            nearestStop = request.child("stop").getValue(String.class);
                        }
                    }
                }

                if (pendingCount == 0) {
                    binding.tvAlightMessage.setText("Monitoring passenger stops…");
                    binding.tvAlightMessage.setTextColor(0xFF9CA3AF);
                } else {
                    String message = pendingCount == 1
                            ? "1 passenger alighting" + (nearestStop != null ? " at " + nearestStop : "")
                            : pendingCount + " passengers alighting" + (nearestStop != null ? " near " + nearestStop : "");

                    binding.tvAlightMessage.setText(message);
                    binding.tvAlightMessage.setTextColor(0xFFF59E0B); // Gold — alert color

                    // Haptic pulse to alert conductor
                    binding.tvAlightMessage.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                }

                Timber.d("AlightRequests updated: %d pending", pendingCount);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Timber.e("AlightRequests listener cancelled: %s", error.getMessage());
                binding.tvAlightMessage.setText("⚠ Alight feed unavailable");
                binding.tvAlightMessage.setTextColor(0xFFEF4444);
            }
        };

        alightRef.addValueEventListener(alightListener);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION 2 — FIREBASE RTDB: LIVE STATUS (KPI tiles)
    // Path: POS_Devices/{serial}/LiveStatus
    //
    // BusLocationService writes here every 5s. This listener pulls live
    // session stats (tickets, cash, gcash) to update the KPI tiles.
    // The Admin's FleetMapPage.tsx also reads from this same node.
    // ─────────────────────────────────────────────────────────────────────────
    private void attachFirebaseLiveStatusListener() {
        liveStatusRef = FirebaseDatabase.getInstance()
                .getReference(RTDB_ROOT_DEVICES)
                .child(deviceSerial)
                .child(RTDB_NODE_LIVE);

        liveStatusListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;

                // Read live stats directly from RTDB (written by BusLocationService or TicketActivity)
                Long totalCash  = snapshot.child("totalCash").getValue(Long.class);
                Long totalGcash = snapshot.child("totalGcash").getValue(Long.class);
                Long regCount   = snapshot.child("regularCount").getValue(Long.class);
                Long stuCount   = snapshot.child("studentCount").getValue(Long.class);
                Long senCount   = snapshot.child("seniorCount").getValue(Long.class);
                String busNum   = snapshot.child("busNumber").getValue(String.class);
                String loop     = snapshot.child("currentLoop").getValue(String.class);

                long cash  = totalCash  != null ? totalCash  : 0;
                long gcash = totalGcash != null ? totalGcash : 0;
                long total = (regCount != null ? regCount : 0)
                           + (stuCount != null ? stuCount : 0)
                           + (senCount != null ? senCount : 0);

                // Update KPI tiles with count-up animation
                animateCount(binding.tvLiveCash,   "₱%,d", 0, (int) cash);
                animateCount(binding.tvLiveGcash,  "₱%,d", 0, (int) gcash);
                animateCount(binding.tvTicketsToday, "%,d", 0, (int) total);

                if (busNum != null) binding.tvBusNumber.setText("BUS " + busNum);
                if (loop   != null) binding.tvCurrentRoute.setText(loop);

                // Stamp sync time
                String time = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date());
                binding.tvLastSyncTime.setText("Last sync: " + time);

                Timber.d("LiveStatus updated — cash=%d gcash=%d pax=%d", cash, gcash, total);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Timber.e("LiveStatus listener cancelled: %s", error.getMessage());
            }
        };

        liveStatusRef.addValueEventListener(liveStatusListener);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION 3 — GYRO PARALLAX (SensorManager accelerometer → hero card tilt)
    // ─────────────────────────────────────────────────────────────────────────
    private void setupGyroParallax() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;

        // Low-pass filter to smooth jitter
        smoothedX = smoothedX + TILT_ALPHA * (event.values[0] - smoothedX);
        smoothedY = smoothedY + TILT_ALPHA * (event.values[1] - smoothedY);

        // Clamp to ±MAX_TILT_DEG and apply rotation
        float tiltX = Math.max(-MAX_TILT_DEG, Math.min(MAX_TILT_DEG, smoothedY * 1.2f));
        float tiltY = Math.max(-MAX_TILT_DEG, Math.min(MAX_TILT_DEG, -smoothedX * 1.2f));

        binding.cardTripStatus.setRotationX(tiltX);
        binding.cardTripStatus.setRotationY(tiltY);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { /* unused */ }

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION 4 — STAGGER-IN ANIMATIONS
    // ─────────────────────────────────────────────────────────────────────────
    private void setupAnimations() {
        View[] staggerViews = {
            binding.cardTripStatus,
            binding.panelAlightNotification,
            binding.rowKpi,
            binding.gridActions,
            binding.cardSyncStatus
        };

        int delayMs = 80;
        for (View v : staggerViews) {
            v.animate()
             .alpha(1f)
             .translationY(0f)
             .setDuration(450)
             .setStartDelay(delayMs)
             .setInterpolator(new DecelerateInterpolator(1.8f))
             .start();
            delayMs += 100;
        }

        applyPressEffect(binding.btnIssueTicket);
        applyPressEffect(binding.btnScanner);
        applyPressEffect(binding.btnViewMap);
        applyPressEffect(binding.btnRemit);
    }

    /** Scale 0.96× on press + haptic confirm on release */
    private void applyPressEffect(View view) {
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(80).start();
                    break;
                case MotionEvent.ACTION_UP:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                    v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                    break;
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                    break;
            }
            return false; // pass click through to OnClickListener
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION 5 — CLICK LISTENERS
    // ─────────────────────────────────────────────────────────────────────────
    private void setupClickListeners() {
        binding.btnIssueTicket.setOnClickListener(v ->
            startActivity(new Intent(this, TicketActivity.class)));

        binding.btnScanner.setOnClickListener(v ->
            startActivity(new Intent(this, GcashQrActivity.class)));

        binding.btnViewMap.setOnClickListener(v ->
            Toast.makeText(this, "Live Map — Phase 7", Toast.LENGTH_SHORT).show());

        binding.btnRemit.setOnClickListener(v ->
            startActivity(new Intent(this, TicketHistoryActivity.class)));

        // Bottom nav
        binding.bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_ticket) {
                binding.btnIssueTicket.performClick();
                return true;
            } else if (id == R.id.nav_map) {
                binding.btnViewMap.performClick();
                return true;
            } else if (id == R.id.nav_profile) {
                Toast.makeText(this, "Profile — coming soon", Toast.LENGTH_SHORT).show();
                return true;
            } else if (id == R.id.nav_notifications) {
                Toast.makeText(this, "Notifications — Phase 9", Toast.LENGTH_SHORT).show();
                return true;
            }
            return true; // nav_home is current screen
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION 6 — HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /** Sets greeting text based on time of day */
    private void setupGreeting() {
        int hour = new java.util.Calendar.Builder().build().get(java.util.Calendar.HOUR_OF_DAY);
        String greeting;
        if (hour < 12)      greeting = "Good morning,";
        else if (hour < 17) greeting = "Good afternoon,";
        else                greeting = "Good evening,";

        binding.tvGreeting.setText(greeting);

        // Load conductor name from session prefs (set during login)
        SharedPreferences prefs = getSharedPreferences("buscorp_session", MODE_PRIVATE);
        String name = prefs.getString("conductor_name", "Conductor");
        binding.tvUserName.setText(name);
    }

    /**
     * Resolves the device serial used as the Firebase RTDB key.
     * Format matches what BusLocationService writes: device model, sanitized.
     * Falls back to SharedPreferences "deviceSerial" set at login/trip start.
     */
    private void resolveDeviceSerial() {
        SharedPreferences prefs = getSharedPreferences("buscorp_session", MODE_PRIVATE);
        deviceSerial = prefs.getString("deviceSerial", null);

        if (deviceSerial == null || deviceSerial.isEmpty()) {
            // Derive from Android device model — same logic as BusLocationService
            deviceSerial = android.os.Build.MODEL
                    .replaceAll("[^a-zA-Z0-9]", "_")
                    .toUpperCase();
            Timber.w("deviceSerial not in prefs, using derived: %s", deviceSerial);
        }

        Timber.d("Firebase RTDB device path: %s/%s", RTDB_ROOT_DEVICES, deviceSerial);
    }

    /** ValueAnimator count-up for KPI tiles */
    private void animateCount(TextView view, String format, int from, int to) {
        ValueAnimator animator = ValueAnimator.ofInt(from, to);
        animator.setDuration(1400);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a ->
            view.setText(String.format(Locale.getDefault(), format, (int) a.getAnimatedValue())));
        animator.start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LIFECYCLE
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    protected void onResume() {
        super.onResume();
        // Re-register gyro sensor on resume
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer,
                    SensorManager.SENSOR_DELAY_GAME);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Unregister to save battery when not in foreground
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Always clean up Firebase listeners to prevent memory leaks
        if (alightRef != null && alightListener != null) {
            alightRef.removeEventListener(alightListener);
            Timber.d("AlightRequests listener removed");
        }
        if (liveStatusRef != null && liveStatusListener != null) {
            liveStatusRef.removeEventListener(liveStatusListener);
            Timber.d("LiveStatus listener removed");
        }
    }

    private void observeSyncStatus() {
        WorkManager.getInstance(this).getWorkInfosByTagLiveData("OfflineSyncWorker").observe(this, workInfos -> {
            if (workInfos != null && !workInfos.isEmpty()) {
                WorkInfo info = workInfos.get(0);
                if (info.getState() == WorkInfo.State.RUNNING) {
                    binding.syncDot.setBackgroundResource(R.drawable.circle_green_pulse);
                    android.widget.TextView tv = (android.widget.TextView) binding.syncBadge.getChildAt(1);
                    tv.setText("SYNCING...");
                    tv.setTextColor(0xFF10B981); // Green
                } else if (info.getState() == WorkInfo.State.FAILED) {
                    binding.syncDot.setBackgroundResource(R.drawable.circle_red);
                    android.widget.TextView tv = (android.widget.TextView) binding.syncBadge.getChildAt(1);
                    tv.setText("SYNC FAILED");
                    tv.setTextColor(0xFFEF4444); // Red
                } else if (info.getState() == WorkInfo.State.ENQUEUED) {
                    binding.syncDot.setBackgroundResource(R.drawable.circle_amber);
                    android.widget.TextView tv = (android.widget.TextView) binding.syncBadge.getChildAt(1);
                    tv.setText("PENDING");
                    tv.setTextColor(0xFFF59E0B); // Amber
                }
            }
        });

        viewModel.getConflictCount().observe(this, count -> {
            if (count > 0) {
                binding.syncDot.setBackgroundResource(R.drawable.circle_red);
                android.widget.TextView tv = (android.widget.TextView) binding.syncBadge.getChildAt(1);
                tv.setText(count + " CONFLICTS");
                tv.setTextColor(0xFFEF4444); // Red
            }
        });
    }
}