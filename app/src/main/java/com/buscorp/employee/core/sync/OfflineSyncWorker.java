package com.buscorp.employee.core.sync;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.buscorp.employee.core.db.AppDatabase;
import com.buscorp.employee.core.db.SyncConflictDao;
import com.buscorp.employee.core.db.SyncConflictEntity;
import com.buscorp.employee.core.db.TicketDao;
import com.buscorp.employee.core.db.TicketEntity;

import java.util.ArrayList;
import java.util.List;

public class OfflineSyncWorker extends Worker {

    private static final String TAG = "OfflineSyncWorker";
    private final TicketDao ticketDao;
    private final SyncConflictDao syncConflictDao;

    public OfflineSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        AppDatabase db = AppDatabase.getInstance(context);
        ticketDao = db.ticketDao();
        syncConflictDao = db.syncConflictDao();
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Starting offline sync...");

        List<TicketEntity> unsynced = ticketDao.getUnsyncedTickets();
        
        if (unsynced == null || unsynced.isEmpty()) {
            Log.d(TAG, "No unsynced tickets found. Sync complete.");
            return Result.success();
        }

        Log.d(TAG, "Found " + unsynced.size() + " unsynced tickets. Attempting sync...");

        try {
            for (TicketEntity ticket : unsynced) {
                // In production, we'd use Retrofit here to push the ticket to Supabase.
                // We'll simulate the network call and potential failure modes.
                
                // Simulate network latency
                Thread.sleep(1000);
                
                // --- Edge-Case QA: Simulate a 409 Conflict error for testing ---
                // If a ticket passenger type is "CONFLICT_TEST", trigger a conflict.
                if ("CONFLICT_TEST".equals(ticket.getPassengerType())) {
                    Log.w(TAG, "Conflict detected for ticket " + ticket.getId());
                    
                    // 1. Log to sync_conflicts table
                    SyncConflictEntity conflict = new SyncConflictEntity(
                            ticket.getId(), ticket.getOrigin(), ticket.getDestination(),
                            ticket.getFare(), ticket.getPassengerType(), ticket.getTimestamp(),
                            "409 Conflict: Record modified upstream."
                    );
                    syncConflictDao.insertConflict(conflict);
                    
                    // 2. Remove from normal queue so it doesn't infinitely loop
                    ticketDao.delete(ticket);
                    continue; // Skip the regular sync success flow
                }

                // If successful:
                ticketDao.markAsSynced(java.util.Collections.singletonList(ticket.getId()));
                Log.d(TAG, "Successfully synced ticket: " + ticket.getId());
            }

            return Result.success();
            
        } catch (Exception e) {
            Log.e(TAG, "Error syncing tickets: ", e);
            return Result.retry();
        }
    }
}
