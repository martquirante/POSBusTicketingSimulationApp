package com.example.possantransbusticketingsystemapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class SelectionLoopActivity extends AppCompatActivity {

    // Variables para sa data na ipinasa galing Home
    private String busNo, driver, conductor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_selection_loop);

        // 1. SALUHIN ANG DATA galing MainActivity
        Intent previousIntent = getIntent();
        busNo = previousIntent.getStringExtra("BUS_NO");
        driver = previousIntent.getStringExtra("DRIVER");
        conductor = previousIntent.getStringExtra("CONDUCTOR");

        // 2. HANAPIN ANG MGA BUTTONS SA LAYOUT
        Button btnFVR = findViewById(R.id.btnLoopFVR);
        Button btnStCruz = findViewById(R.id.btnLoopStCruz);
        Button btnBack = findViewById(R.id.btnBackLoop);

        // 3. LOGIC: FVR to ST.CRUZ (Forward Loop)
        btnFVR.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // false = Forward Loop (Standard route)
                goToRouteList(false);
            }
        });

        // 4. LOGIC: ST.CRUZ to FVR (Reverse Loop)
        btnStCruz.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // true = Reverse Loop (Pabalik)
                goToRouteList(true);
            }
        });

        // Back Button: Bumalik sa Home
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish(); // Isara ang activity na ito
            }
        });
    }

    // Helper method para hindi paulit-ulit ang code
    private void goToRouteList(boolean isReverse) {
        // Papunta sa RouteListActivity
        Intent intent = new Intent(SelectionLoopActivity.this, RouteListActivity.class);

        // IPASA ULIT ANG DATA (Para hindi mawala ang Bus Info)
        intent.putExtra("BUS_NO", busNo);
        intent.putExtra("DRIVER", driver);
        intent.putExtra("CONDUCTOR", conductor);

        // IPASA ANG DIRECTION (Importante ito para sa Folder Name mamaya)
        intent.putExtra("IS_REVERSE", isReverse);

        startActivity(intent);
    }
}