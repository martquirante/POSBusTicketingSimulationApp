package com.example.possantransbusticketingsystemapp;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class TermsActivity extends BaseActivity { // Extends BaseActivity para sa Theme

    private Button btnAccept;
    private CheckBox cbAgree;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_terms); // Ito yung XML mo: activity_terms.xml

        cbAgree = findViewById(R.id.cbAgree);
        btnAccept = findViewById(R.id.btnAcceptTerms);

        // Initial State: Disabled
        btnAccept.setEnabled(false);
        btnAccept.setAlpha(0.5f); // Fade effect para mukhang disabled

        // Checkbox Listener
        cbAgree.setOnCheckedChangeListener((buttonView, isChecked) -> {
            btnAccept.setEnabled(isChecked);
            if (isChecked) {
                btnAccept.setAlpha(1.0f); // Full opacity
                // Optional: Change color if needed, but backgroundTint in XML usually handles it
                btnAccept.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.brand_primary)));
            } else {
                btnAccept.setAlpha(0.5f); // Faded
                btnAccept.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.text_secondary)));
            }
        });

        // Button Listener
        btnAccept.setOnClickListener(v -> {
            if (cbAgree.isChecked()) {
                // Save state (Optional)
                getSharedPreferences("SantransPrefs", MODE_PRIVATE).edit()
                        .putBoolean("TermsAccepted", true)
                        .apply();

                Toast.makeText(this, "Terms Accepted", Toast.LENGTH_SHORT).show();

                // Proceed to Next Screen (Selection Loop or Main Activity)
                Intent intent = new Intent(TermsActivity.this, SelectionLoopActivity.class);
                startActivity(intent);
                finish(); // Close Terms screen
            }
        });
    }
}