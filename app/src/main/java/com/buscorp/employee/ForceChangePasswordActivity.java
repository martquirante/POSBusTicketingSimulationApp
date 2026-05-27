package com.buscorp.employee;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.airbnb.lottie.LottieAnimationView;
import com.buscorp.employee.core.auth.SessionManager;
import com.buscorp.employee.core.network.SupabaseApi;
import com.buscorp.employee.core.util.Animations;
import com.google.gson.JsonObject;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class ForceChangePasswordActivity extends AppCompatActivity {

    @Inject
    SupabaseApi api;

    @Inject
    SessionManager sessionManager;

    private EditText etNewPassword;
    private EditText etConfirmPassword;
    private TextView tvForceError;
    private Button btnChangePassword;
    private LottieAnimationView passwordLoading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_force_change_password);

        etNewPassword = findViewById(R.id.etNewPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        tvForceError = findViewById(R.id.tvForceError);
        btnChangePassword = findViewById(R.id.btnChangePassword);
        passwordLoading = findViewById(R.id.passwordLoading);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                showError("Password change is required before opening the conductor workspace.");
            }
        });

        btnChangePassword.setOnClickListener(v -> Animations.press(v, this::changePassword));
    }

    private void changePassword() {
        String password = etNewPassword.getText().toString();
        String confirm = etConfirmPassword.getText().toString();

        if (password.length() < 8) {
            showError("Password must be at least 8 characters.");
            return;
        }
        if (!password.equals(confirm)) {
            showError("Passwords do not match.");
            return;
        }

        setLoading(true);
        JsonObject body = new JsonObject();
        body.addProperty("password", password);
        api.updateUser(body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                runOnUiThread(() -> {
                    setLoading(false);
                    if (response.isSuccessful()) {
                        sessionManager.setMustChangePassword(false);
                        startActivity(new Intent(ForceChangePasswordActivity.this, ConductorDashboardActivity.class));
                        finish();
                    } else {
                        showError("Unable to update password. Please try again.");
                    }
                });
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                runOnUiThread(() -> {
                    setLoading(false);
                    showError("Network error. Please try again.");
                });
            }
        });
    }

    private void showError(String message) {
        tvForceError.setText(message);
        tvForceError.setVisibility(View.VISIBLE);
    }

    private void setLoading(boolean loading) {
        btnChangePassword.setEnabled(!loading);
        passwordLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            passwordLoading.playAnimation();
        } else {
            passwordLoading.cancelAnimation();
        }
    }
}
