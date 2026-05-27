package com.buscorp.employee;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.buscorp.employee.core.db.AppDatabase;
import com.buscorp.employee.core.db.TicketDao;
import com.buscorp.employee.core.db.TicketEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;

@HiltViewModel
public class TicketHistoryViewModel extends ViewModel {

    private final TicketDao ticketDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    
    private final MutableLiveData<List<TicketEntity>> ticketsData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loadingState = new MutableLiveData<>();
    
    private List<TicketEntity> allTicketsCache = new ArrayList<>();

    @Inject
    public TicketHistoryViewModel(@ApplicationContext Context context) {
        this.ticketDao = AppDatabase.getInstance(context).ticketDao();
    }

    public LiveData<List<TicketEntity>> getTicketsData() { return ticketsData; }
    public LiveData<Boolean> getLoadingState() { return loadingState; }

    public void loadHistory() {
        loadingState.postValue(true);
        executor.execute(() -> {
            try {
                Thread.sleep(800); // Artificial delay to show Shimmer gracefully
                allTicketsCache = ticketDao.getAllTickets();
                ticketsData.postValue(allTicketsCache);
            } catch (Exception e) {
                Log.e("HistoryVM", "Error loading history", e);
            } finally {
                loadingState.postValue(false);
            }
        });
    }

    public void filter(String type) {
        if (allTicketsCache.isEmpty()) return;
        
        executor.execute(() -> {
            if ("All".equalsIgnoreCase(type)) {
                ticketsData.postValue(allTicketsCache);
                return;
            }
            
            List<TicketEntity> filtered = new ArrayList<>();
            for (TicketEntity t : allTicketsCache) {
                if ("Discounted".equalsIgnoreCase(type)) {
                    if (t.getPassengerType().equalsIgnoreCase("Senior") || t.getPassengerType().equalsIgnoreCase("PWD")) {
                        filtered.add(t);
                    }
                } else if (t.getPassengerType().equalsIgnoreCase(type)) {
                    filtered.add(t);
                }
            }
            ticketsData.postValue(filtered);
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}
