package com.example.possantransbusticketingsystemapp;

import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

public class SettingsActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView message = new TextView(this);
        message.setText("Settings");
        message.setGravity(Gravity.CENTER);
        message.setTextSize(22);
        setContentView(message);
    }
}
