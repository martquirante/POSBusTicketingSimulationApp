package com.buscorp.employee.services;

import android.util.Log;

import com.buscorp.employee.BuildConfig;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * AuthService — Now uses Supabase PostgreSQL for authentication
 * via the Supabase REST API instead of Firebase Auth.
 */
public class AuthService {

    private static final String TAG = "AuthService";
    private final OkHttpClient client;
    
    // Simple mock state for this simulation app
    private static String currentUserUid = null;
    private static String currentAccessToken = null;

    public interface AuthCallback {
        void onSuccess(String uid);
        void onError(String message);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onError(String message);
    }

    public AuthService() {
        client = new OkHttpClient();
    }

    /**
     * Sign in with email and password via Supabase.
     */
    public void login(String email, String password, AuthCallback callback) {
        if (email == null || email.trim().isEmpty()) {
            callback.onError("Please enter your email address.");
            return;
        }
        if (password == null || password.trim().isEmpty()) {
            callback.onError("Please enter your password.");
            return;
        }

        String url = BuildConfig.SUPABASE_URL + "/auth/v1/token?grant_type=password";
        
        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("email", email.trim());
            jsonBody.put("password", password);
        } catch (JSONException e) {
            callback.onError("Error formatting credentials.");
            return;
        }

        RequestBody body = RequestBody.create(jsonBody.toString(), MediaType.parse("application/json; charset=utf-8"));
        
        Request request = new Request.Builder()
                .url(url)
                .addHeader("apikey", BuildConfig.SUPABASE_KEY)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Login request failed", e);
                callback.onError("Network error during login.");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String responseBody = response.body().string();
                        JSONObject jsonResponse = new JSONObject(responseBody);
                        
                        // Supabase returns 'access_token' and 'user' object
                        if (jsonResponse.has("user")) {
                            JSONObject user = jsonResponse.getJSONObject("user");
                            currentUserUid = user.getString("id");
                            currentAccessToken = jsonResponse.optString("access_token", "");
                            callback.onSuccess(currentUserUid);
                        } else {
                            callback.onError("Login successful but user data missing.");
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Failed to parse login response", e);
                        callback.onError("Invalid response from server.");
                    }
                } else {
                    String errorMsg = "Login failed.";
                    if (response.body() != null) {
                        try {
                            JSONObject errorJson = new JSONObject(response.body().string());
                            errorMsg = errorJson.optString("error_description", errorJson.optString("msg", "Invalid credentials."));
                        } catch (JSONException ignored) {}
                    }
                    callback.onError(errorMsg);
                }
            }
        });
    }

    public void logout(SimpleCallback callback) {
        currentUserUid = null;
        currentAccessToken = null;
        callback.onSuccess();
    }

    public String getCurrentUserId() {
        return currentUserUid;
    }

    public boolean isUserSignedIn() {
        return currentUserUid != null;
    }
}