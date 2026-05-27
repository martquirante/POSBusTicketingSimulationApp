package com.buscorp.employee;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class LoginActivity extends AppCompatActivity {

    private ImageView ivHeroBus;
    private LinearLayout loginCard;
    private MaterialButton btnLogin;
    private LinearLayout btnBiometric;
    private TextInputEditText etEmail, etPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(R.style.Theme_BusCorp_Login);
        setContentView(R.layout.activity_login);

        initViews();
        setupAnimations();
        setupClickListeners();
    }

    private void initViews() {
        ivHeroBus = findViewById(R.id.ivHeroBus);
        loginCard = findViewById(R.id.loginCard);
        btnLogin = findViewById(R.id.btnLogin);
        btnBiometric = findViewById(R.id.btnBiometric);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
    }

    private void setupAnimations() {
        // Entrance animation
        ivHeroBus.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(800)
                .setInterpolator(new OvershootInterpolator(1.2f))
                .setStartDelay(100)
                .start();

        loginCard.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(600)
                .setInterpolator(new DecelerateInterpolator(1.5f))
                .setStartDelay(300)
                .start();

        // Button scale down on touch
        applyPressEffect(btnLogin);
        applyPressEffect(btnBiometric);
    }

    private void applyPressEffect(View view) {
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start();
                    break;
                case MotionEvent.ACTION_UP:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
                    v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                    break;
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
                    break;
            }
            return false;
        });
    }

    private void setupClickListeners() {
        btnLogin.setOnClickListener(v -> {
            // Placeholder auth logic
            String email = etEmail.getText() != null ? etEmail.getText().toString() : "";
            String pass = etPassword.getText() != null ? etPassword.getText().toString() : "";
            
            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Please enter credentials", Toast.LENGTH_SHORT).show();
                return;
            }

            // Simulate network delay
            btnLogin.setEnabled(false);
            btnLogin.setText("Authenticating...");
            
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                startActivity(new Intent(LoginActivity.this, ConductorDashboardActivity.class));
                finish();
            }, 1000);
        });

        btnBiometric.setOnClickListener(v -> {
            Toast.makeText(this, "Biometric Auth Triggered (Placeholder)", Toast.LENGTH_SHORT).show();
            // In Phase 5/6, actual AndroidX BiometricPrompt will be invoked here.
        });
    }
}
