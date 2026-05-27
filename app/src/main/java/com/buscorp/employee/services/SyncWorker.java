package com.buscorp.employee.services;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.buscorp.employee.BuildConfig;
import com.buscorp.employee.data.local.AppDatabase;
import com.buscorp.employee.data.local.TicketEntity;

import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SyncWorker extends Worker {

    private static final String TAG = "SyncWorker";
    private final AppDatabase db;
    private final OkHttpClient client;

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        db = AppDatabase.getInstance(context);
        client = new OkHttpClient();
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Starting offline sync...");

        List<TicketEntity> unsyncedTickets = db.ticketDao().getUnsyncedTickets();
        if (unsyncedTickets.isEmpty()) {
            Log.d(TAG, "No pending tickets to sync.");
            return Result.success();
        }

        boolean allSuccess = true;

        for (TicketEntity ticket : unsyncedTickets) {
            try {
                boolean success = pushTicketToSupabase(ticket);
                if (success) {
                    db.ticketDao().markAsSynced(ticket.ticket_id);
                    Log.d(TAG, "Successfully synced ticket: " + ticket.ticket_id);
                } else {
                    allSuccess = false;
                    Log.e(TAG, "Failed to sync ticket: " + ticket.ticket_id);
                }
            } catch (Exception e) {
                allSuccess = false;
                Log.e(TAG, "Exception syncing ticket: " + ticket.ticket_id, e);
            }
        }

        if (allSuccess) {
            return Result.success();
        } else {
            return Result.retry();
        }
    }

    private boolean pushTicketToSupabase(TicketEntity ticket) throws Exception {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id", ticket.ticket_id);
        jsonObject.put("route", ticket.route);
        jsonObject.put("origin", ticket.origin);
        jsonObject.put("destination", ticket.destination);
        jsonObject.put("fare", ticket.fare);
        jsonObject.put("passenger_type", ticket.passenger_type);
        jsonObject.put("timestamp", ticket.timestamp);
        
        if (ticket.conductor_id != null && !ticket.conductor_id.isEmpty()) {
            jsonObject.put("conductor_id", ticket.conductor_id);
        }
        if (ticket.bus_id != null && !ticket.bus_id.isEmpty()) {
            jsonObject.put("bus_id", ticket.bus_id);
        }

        RequestBody body = RequestBody.create(
                jsonObject.toString(),
                MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(BuildConfig.SUPABASE_URL + "/rest/v1/tickets")
                .addHeader("apikey", BuildConfig.SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_KEY)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=minimal")
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            // 201 Created or 200 OK
            if (response.isSuccessful()) {
                return true;
            } else {
                Log.e(TAG, "Supabase error: " + response.code() + " " + response.message());
                // If it's a 409 Conflict, it means it already exists, so we mark it as synced.
                return response.code() == 409;
            }
        }
    }
}
