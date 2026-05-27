package com.example.possantransbusticketingsystemapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Ito ang magli-link sa layout na ginawa mo kanina
        setContentView(R.layout.activity_splash);

        // Itago ang Action Bar (yung blue bar sa taas) para full screen
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Timer Logic: Maghihintay ng 3 seconds (3000ms) bago lumipat
        // Gawing 1500 (1.5 seconds) para mabilis
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(SplashActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
            }
        }, 1500); // <-- Mas mabilis, mas okay sa trabaho
    }
}