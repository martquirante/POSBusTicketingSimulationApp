package com.example.possantransbusticketingsystemapp;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.MapTileIndex;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class FareDataActivity extends BaseActivity implements SensorEventListener {

    // --- MAP & SENSOR COMPONENTS ---
    private MapView mapView;
    private MyLocationNewOverlay locationOverlay;
    private Polyline routeOverlay;
    private SensorManager sensorManager;
    private Sensor accelerometer, magnetometer;
    private float[] gravity, geomagnetic;
    private float azimuth = 0f;
    private boolean isCompassEnabled = false;

    // --- FIREBASE ---
    private DatabaseReference liveStatusRef;
    private DatabaseReference routesRef;
    private ValueEventListener mLiveListener;

    // --- UI & DATA ---
    private TextView tvPax, tvCollected, tvSpeedometer;
    private boolean isSatellite = false;
    private String deviceSerial;
    private String currentRouteName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        Configuration.getInstance().setUserAgentValue(getPackageName());

        setContentView(R.layout.activity_fare_data);

        if (!checkMapPermissions()) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }

        // 1. SETUP FIREBASE & SENSORS
        deviceSerial = android.os.Build.MODEL.replaceAll("[^a-zA-Z0-9]", "_").toUpperCase();
        liveStatusRef = FirebaseDatabase.getInstance().getReference("POS_Devices").child(deviceSerial).child("LiveStatus");
        routesRef = FirebaseDatabase.getInstance().getReference("Routes_Forward");
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        // 2. INIT VIEWS
        mapView = findViewById(R.id.mapView);
        tvPax = findViewById(R.id.tvTotalPaxMap);
        tvCollected = findViewById(R.id.tvTotalCollectedMap);
        tvSpeedometer = findViewById(R.id.tvSpeedMap);

        Button btnBack = findViewById(R.id.btnBackData);
        FloatingActionButton btnMyLocation = findViewById(R.id.btnMyLocation);
        FloatingActionButton btnCompass = findViewById(R.id.btnCompass);

        // 3. MAP CONFIG
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(17.0);
        mapView.getController().setCenter(new GeoPoint(14.8579, 121.0566));

        setupUserLocation();

        // 4. BUTTON LISTENERS
        if(btnBack != null) btnBack.setOnClickListener(v -> finish());

        if (btnMyLocation != null) {
            btnMyLocation.setOnClickListener(v -> {
                if (locationOverlay != null && locationOverlay.getMyLocation() != null) {
                    mapView.getController().animateTo(locationOverlay.getMyLocation());
                    if(isCompassEnabled) isCompassEnabled = false; // Turn off compass mode when focusing
                    mapView.setMapOrientation(0); // Reset orientation
                    locationOverlay.enableFollowLocation();
                } else {
                    Toast.makeText(this, "Searching for GPS...", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnCompass != null) {
            btnCompass.setOnClickListener(v -> {
                isCompassEnabled = !isCompassEnabled;
                if(isCompassEnabled){
                    locationOverlay.disableFollowLocation(); // Disable auto-follow in compass mode
                    Toast.makeText(this, "Compass mode ON", Toast.LENGTH_SHORT).show();
                } else {
                    mapView.setMapOrientation(0); // Reset orientation when turned off
                    Toast.makeText(this, "Compass mode OFF", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(mapView!=null) mapView.onResume();
        if(locationOverlay!=null) locationOverlay.enableMyLocation();
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
        listenToLiveStatus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(mapView!=null) mapView.onPause();
        if(locationOverlay!=null) locationOverlay.disableMyLocation();
        sensorManager.unregisterListener(this);
        if (liveStatusRef != null && mLiveListener != null) liveStatusRef.removeEventListener(mLiveListener);
    }

    private void setupUserLocation() {
        GpsMyLocationProvider provider = new GpsMyLocationProvider(this);
        provider.addLocationSource(LocationManager.NETWORK_PROVIDER);

        locationOverlay = new MyLocationNewOverlay(provider, mapView) {
            @Override
            public void onLocationChanged(Location location, IMyLocationProvider source) {
                super.onLocationChanged(location, source);
                if (location != null) {
                    float speedMps = location.getSpeed();
                    int speedKph = (int) (speedMps * 3.6);
                    if (tvSpeedometer != null) {
                        tvSpeedometer.setText(speedKph + " km/h");
                        tvSpeedometer.setTextColor(speedKph > 60 ? Color.RED : Color.GREEN);
                    }

                    // **SMART ZOOM LOGIC**
                    // This logic adjusts the zoom level based on the current speed.
                    double targetZoom;
                    if (speedKph < 5) {
                        targetZoom = 18.0; // Zoom in very close when stopped or very slow
                    } else if (speedKph < 30) {
                        targetZoom = 17.0; // Standard city driving zoom
                    } else if (speedKph < 60) {
                        targetZoom = 16.0; // Zoom out a bit for faster speeds
                    } else {
                        targetZoom = 15.0; // Zoom out further for highway speeds
                    }
                    mapView.getController().setZoom(targetZoom);

                    updateFirebaseLocation(location.getLatitude(), location.getLongitude(), speedKph);
                }
            }
        };

        // Set custom bus icon
        Drawable busIcon = ContextCompat.getDrawable(this, R.drawable.ic_bus_marker);
        locationOverlay.setPersonIcon(busIcon);

        locationOverlay.enableMyLocation();
        locationOverlay.enableFollowLocation();
        locationOverlay.setDrawAccuracyEnabled(true);
        mapView.getOverlays().add(locationOverlay);
    }

    // SensorEventListener Methods for Compass
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
            gravity = event.values;
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
            geomagnetic = event.values;
        if (gravity != null && geomagnetic != null) {
            float[] R = new float[9];
            float[] I = new float[9];
            boolean success = SensorManager.getRotationMatrix(R, I, gravity, geomagnetic);
            if (success) {
                float[] orientation = new float[3];
                SensorManager.getOrientation(R, orientation);
                azimuth = (float) Math.toDegrees(orientation[0]);
                azimuth = (azimuth + 360) % 360;

                if (isCompassEnabled) {
                    mapView.setMapOrientation(-azimuth);
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    private void listenToLiveStatus() {
        mLiveListener = liveStatusRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    double cash = 0.0, gcash = 0.0;
                    int reg=0, stu=0, sen=0;

                    if (snapshot.child("totalCash").getValue() instanceof Number) cash = ((Number) snapshot.child("totalCash").getValue()).doubleValue();
                    if (snapshot.child("totalGcash").getValue() instanceof Number) gcash = ((Number) snapshot.child("totalGcash").getValue()).doubleValue();
                    if (snapshot.child("regularCount").getValue() instanceof Number) reg = ((Number) snapshot.child("regularCount").getValue()).intValue();
                    if (snapshot.child("studentCount").getValue() instanceof Number) stu = ((Number) snapshot.child("studentCount").getValue()).intValue();
                    if (snapshot.child("seniorCount").getValue() instanceof Number) sen = ((Number) snapshot.child("seniorCount").getValue()).intValue();

                    tvPax.setText(String.valueOf(reg+stu+sen));
                    tvCollected.setText("₱" + String.format("%.2f", cash+gcash));

                    String loopName = snapshot.child("currentLoop").getValue(String.class);
                    if(loopName != null && !loopName.equals(currentRouteName)) {
                        currentRouteName = loopName;
                        fetchRouteFromWeb();
                    }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void fetchRouteFromWeb() {
        routesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                ArrayList<GeoPoint> routePoints = new ArrayList<>();
                boolean routeFound = false;

                for (DataSnapshot route : snapshot.getChildren()) {
                    String origin = route.child("origin").getValue(String.class);
                    String destination = route.child("destination").getValue(String.class);

                    if (currentRouteName != null && origin != null && destination != null) {
                        DataSnapshot geometry = route.child("geometry").child("coordinates");

                        if (geometry.exists()) {
                            for (DataSnapshot coord : geometry.getChildren()) {
                                Double lng = coord.child("0").getValue(Double.class);
                                Double lat = coord.child("1").getValue(Double.class);

                                if (lat != null && lng != null) {
                                    routePoints.add(new GeoPoint(lat, lng));
                                }
                            }
                            routeFound = true;
                            break; 
                        }
                    }
                }

                if (routeFound) {
                    drawRouteOnMap(routePoints);
                }
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void drawRouteOnMap(ArrayList<GeoPoint> points) {
        if (mapView == null || points.size() < 2) return;

        if (routeOverlay != null) mapView.getOverlays().remove(routeOverlay);

        routeOverlay = new Polyline();
        routeOverlay.setPoints(points);
        routeOverlay.setColor(Color.parseColor("#0066CC"));
        routeOverlay.setWidth(15.0f);

        mapView.getOverlayManager().add(0, routeOverlay);
        mapView.invalidate();
    }

    private void updateFirebaseLocation(double lat, double lng, int speed) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("lat", lat);
        updates.put("lng", lng);
        updates.put("speed", speed);
        updates.put("lastUpdate", System.currentTimeMillis());

        liveStatusRef.updateChildren(updates);
    }

    public static class GoogleHybridTileSource extends XYTileSource {
        public GoogleHybridTileSource() {
            super("Google Hybrid", 0, 19, 256, ".png", new String[]{"https://mt0.google.com"});
        }
        @Override public String getTileURLString(long pMapTileIndex) {
            return getBaseUrl() + "/vt/lyrs=y&x=" + MapTileIndex.getX(pMapTileIndex) + "&y=" + MapTileIndex.getY(pMapTileIndex) + "&z=" + MapTileIndex.getZoom(pMapTileIndex);
        }
    }

    private boolean checkMapPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
}
