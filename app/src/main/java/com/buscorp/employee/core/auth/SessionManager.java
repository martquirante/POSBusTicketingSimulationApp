package com.buscorp.employee.core.auth;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import dagger.hilt.android.qualifiers.ApplicationContext;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class SessionManager {

    private static final String PREFS_NAME = "buscorp_secure_session";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_MUST_CHANGE_PASSWORD = "must_change_password";
    private static final String KEY_BIOMETRIC_ENABLED = "biometric_enabled";

    private final SharedPreferences preferences;

    @Inject
    public SessionManager(@ApplicationContext Context context) {
        this.preferences = createEncryptedPreferences(context);
    }

    private SharedPreferences createEncryptedPreferences(Context context) {
        try {
            MasterKey key = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    key,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            throw new IllegalStateException("Unable to open secure Bus Corp. session storage.", e);
        }
    }

    public void saveSession(String userId, String accessToken, String refreshToken, boolean mustChangePassword) {
        preferences.edit()
                .putString(KEY_USER_ID, userId)
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .putBoolean(KEY_MUST_CHANGE_PASSWORD, mustChangePassword)
                .apply();
    }

    public void updateAccessToken(String accessToken, String refreshToken) {
        preferences.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .apply();
    }

    @Nullable
    public String getAccessToken() {
        return preferences.getString(KEY_ACCESS_TOKEN, null);
    }

    @Nullable
    public String getRefreshToken() {
        return preferences.getString(KEY_REFRESH_TOKEN, null);
    }

    @Nullable
    public String getUserId() {
        return preferences.getString(KEY_USER_ID, null);
    }

    public boolean hasSession() {
        return getAccessToken() != null && getUserId() != null;
    }

    public boolean mustChangePassword() {
        return preferences.getBoolean(KEY_MUST_CHANGE_PASSWORD, false);
    }

    public void setMustChangePassword(boolean value) {
        preferences.edit().putBoolean(KEY_MUST_CHANGE_PASSWORD, value).apply();
    }

    public boolean isBiometricEnabled() {
        return preferences.getBoolean(KEY_BIOMETRIC_ENABLED, false);
    }

    public void setBiometricEnabled(boolean value) {
        preferences.edit().putBoolean(KEY_BIOMETRIC_ENABLED, value).apply();
    }

    public void clear() {
        preferences.edit().clear().apply();
    }
}
