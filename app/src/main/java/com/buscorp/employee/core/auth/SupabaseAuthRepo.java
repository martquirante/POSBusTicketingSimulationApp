package com.buscorp.employee.core.auth;

import androidx.annotation.NonNull;

import com.buscorp.employee.core.network.SupabaseApi;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.inject.Inject;
import javax.inject.Singleton;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@Singleton
public class SupabaseAuthRepo {

    public interface LoginCallback {
        void onSuccess(boolean mustChangePassword);
        void onDenied(String message);
        void onError(String message);
    }

    private final SupabaseApi api;
    private final SessionManager sessionManager;

    @Inject
    public SupabaseAuthRepo(SupabaseApi api, SessionManager sessionManager) {
        this.api = api;
        this.sessionManager = sessionManager;
    }

    public void loginConductor(String email, String password, LoginCallback callback) {
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("password", password);

        api.passwordLogin(body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    callback.onError("Invalid credentials. Please check your email and password.");
                    return;
                }

                JsonObject auth = response.body();
                JsonObject user = auth.getAsJsonObject("user");
                if (user == null || !user.has("id")) {
                    callback.onError("Login succeeded but the employee profile was missing.");
                    return;
                }

                String userId = user.get("id").getAsString();
                String accessToken = auth.get("access_token").getAsString();
                String refreshToken = auth.has("refresh_token") ? auth.get("refresh_token").getAsString() : "";

                checkConductorRole(userId, accessToken, refreshToken, callback);
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                callback.onError("Network error. Please try again.");
            }
        });
    }

    private void checkConductorRole(String userId, String accessToken, String refreshToken, LoginCallback callback) {
        JsonObject roleBody = new JsonObject();
        roleBody.addProperty("_user_id", userId);
        roleBody.addProperty("_role", RoleGate.ROLE_CONDUCTOR);

        api.hasRole(roleBody).enqueue(new Callback<Boolean>() {
            @Override
            public void onResponse(@NonNull Call<Boolean> call, @NonNull Response<Boolean> response) {
                Boolean allowed = response.body();
                if (!response.isSuccessful() || allowed == null || !allowed) {
                    sessionManager.clear();
                    callback.onDenied("Access denied. Conductor role required. Contact Admin.");
                    return;
                }

                loadProfileGate(userId, accessToken, refreshToken, callback);
            }

            @Override
            public void onFailure(@NonNull Call<Boolean> call, @NonNull Throwable t) {
                callback.onError("Unable to verify conductor role. Please try again.");
            }
        });
    }

    private void loadProfileGate(String userId, String accessToken, String refreshToken, LoginCallback callback) {
        api.getProfileGate("id.eq." + userId).enqueue(new Callback<JsonArray>() {
            @Override
            public void onResponse(@NonNull Call<JsonArray> call, @NonNull Response<JsonArray> response) {
                boolean mustChangePassword = true;
                if (response.isSuccessful() && response.body() != null && response.body().size() > 0) {
                    JsonObject profile = response.body().get(0).getAsJsonObject();
                    if (profile.has("must_change_password") && !profile.get("must_change_password").isJsonNull()) {
                        mustChangePassword = profile.get("must_change_password").getAsBoolean();
                    }
                }

                sessionManager.saveSession(userId, accessToken, refreshToken, mustChangePassword);
                callback.onSuccess(mustChangePassword);
            }

            @Override
            public void onFailure(@NonNull Call<JsonArray> call, @NonNull Throwable t) {
                callback.onError("Unable to load employee profile. Please try again.");
            }
        });
    }

    public void logout() {
        api.logout().enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                sessionManager.clear();
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                sessionManager.clear();
            }
        });
    }
}
