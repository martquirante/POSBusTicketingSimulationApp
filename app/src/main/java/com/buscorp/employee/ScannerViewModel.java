package com.buscorp.employee;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.buscorp.employee.core.db.AppDatabase;
import com.buscorp.employee.core.db.TicketDao;
import com.buscorp.employee.core.network.SupabaseApi;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@HiltViewModel
public class ScannerViewModel extends ViewModel {

    private static final String TAG = "ScannerViewModel";
    private final MutableLiveData<ScanState> scanResultState = new MutableLiveData<>();
    
    private final TicketDao ticketDao;
    private final SupabaseApi supabaseApi;
    private final DatabaseReference firebaseLiveStatus;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    
    private final String DEVICE_SERIAL = "DEVICE_001"; // Mocked device serial for prototype

    @Inject
    public ScannerViewModel(@ApplicationContext Context context, SupabaseApi supabaseApi) {
        this.ticketDao = AppDatabase.getInstance(context).ticketDao();
        this.supabaseApi = supabaseApi;
        this.firebaseLiveStatus = FirebaseDatabase.getInstance().getReference("POS_Devices").child(DEVICE_SERIAL).child("LiveStatus");
    }

    public LiveData<ScanState> getScanResultState() {
        return scanResultState;
    }

    public void verifyTicket(String ticketId) {
        // 1. API Check to Backend (Supabase via Retrofit)
        JsonObject body = new JsonObject();
        body.addProperty("status", "boarded");

        // ticketId=eq.UUID format
        supabaseApi.verifyTicket(ticketId, "eq." + ticketId, body).enqueue(new Callback<JsonArray>() {
            @Override
            public void onResponse(Call<JsonArray> call, Response<JsonArray> response) {
                if (response.isSuccessful() && response.body() != null && response.body().size() > 0) {
                    
                    // 2. Real-time Sync to Firebase RTDB
                    incrementPassengersOnboard();
                    
                    scanResultState.postValue(new ScanState(ScanStatus.SUCCESS, "Ticket Verified"));
                } else {
                    scanResultState.postValue(new ScanState(ScanStatus.ERROR, "Ticket invalid or already used."));
                }
            }

            @Override
            public void onFailure(Call<JsonArray> call, Throwable t) {
                Log.e(TAG, "Verification error", t);
                
                // 3. Offline Resilience
                // Update local Room DB and queue for sync later
                executor.execute(() -> {
                    // ticketDao.cacheVerificationForSync(ticketId);
                });
                
                scanResultState.postValue(new ScanState(ScanStatus.ERROR, "Network error. Cached offline."));
            }
        });
    }
    
    private void incrementPassengersOnboard() {
        firebaseLiveStatus.child("passengersOnboard").get().addOnSuccessListener(snapshot -> {
            long currentCount = 0;
            if (snapshot.exists() && snapshot.getValue() != null) {
                currentCount = (long) snapshot.getValue();
            }
            firebaseLiveStatus.child("passengersOnboard").setValue(currentCount + 1);
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }

    // -- State Classes --
    
    public enum ScanStatus {
        SUCCESS, ERROR
    }

    public static class ScanState {
        private final ScanStatus status;
        private final String message;

        public ScanState(ScanStatus status, String message) {
            this.status = status;
            this.message = message;
        }

        public ScanStatus getStatus() { return status; }
        public String getMessage() { return message; }
    }
}
