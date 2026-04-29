package com.example.possantransbusticketingsystemapp;

import android.app.Application;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.firebase.database.FirebaseDatabase;

/**
 * This is the main Application class. It's the first component to run when the application starts.
 * We use this class for app-wide initializations.
 */
public class AppController extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        /*
         * This section reads the user's last saved theme preference from SharedPreferences.
         * SharedPreferences is a simple way on Android to store small amounts of data in key-value pairs.
         * We use "ThemePrefs" as the name of our preference file.
         * The 'mode' variable will hold the saved theme setting:
         * 0 for System Default
         * 1 for Light Mode
         * 2 for Dark Mode
         * If no preference is found, it defaults to 0 (System Default).
         */
        SharedPreferences prefs = getSharedPreferences("ThemePrefs", MODE_PRIVATE);
        int mode = prefs.getInt("mode", 0);

        /*
         * This applies the chosen theme to the entire application before any screen is shown.
         * Technology Used: AppCompatDelegate
         * How it works:
         * AppCompatDelegate allows us to set the night mode for all Activities globally.
         * By calling this in the Application's onCreate, we ensure the correct theme is applied
         * instantly, preventing any flicker or theme change after the first screen is visible.
         */
        switch (mode) {
            case 0: AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM); break;
            case 1: AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO); break;
            case 2: AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES); break;
        }

        /*
         *  This enables Firebase Realtime Database offline capabilities for the entire app.
         *  Technology Used: Firebase Realtime Database Persistence
         *
         *  How it works:
         *  By calling `setPersistenceEnabled(true)`, we instruct the Firebase SDK to keep a local
         *  copy of the database on the device's storage.
         *
         *  1. When the app is offline: Any data writes (e.g., creating a new ticket, updating a location)
         *     are saved to this local cache first. The app remains fully functional as it can still read
         *     and write data to and from this local cache.
         *
         *  2. When the app comes back online: The Firebase SDK automatically and seamlessly synchronizes the
         *     locally saved data with the cloud (the remote Firebase servers). All the changes made while
         *     offline are pushed to the remote database, and any changes from the server are fetched.
         *
         *  This is crucial for an app like a bus ticketing system, ensuring it works reliably even
         *  with unstable internet connections on the road. This line only needs to be called once,
         *  making the Application class the perfect place for it.
         */
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);
    }
}
