package com.buscorp.employee;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import com.buscorp.employee.core.db.AppDatabase;
import com.buscorp.employee.core.db.SyncConflictDao;
import com.buscorp.employee.core.db.TicketDao;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;

@HiltViewModel
public class DashboardViewModel extends ViewModel {

    private final SyncConflictDao syncConflictDao;
    private final TicketDao ticketDao;
    private final SavedStateHandle savedStateHandle;

    @Inject
    public DashboardViewModel(@ApplicationContext Context context, SavedStateHandle savedStateHandle) {
        AppDatabase db = AppDatabase.getInstance(context);
        this.syncConflictDao = db.syncConflictDao();
        this.ticketDao = db.ticketDao();
        this.savedStateHandle = savedStateHandle;
    }

    public LiveData<Integer> getConflictCount() {
        return syncConflictDao.getConflictCountLive();
    }
    
    // Example of SavedStateHandle usage for process death protection
    public void setOnboardCount(int count) {
        savedStateHandle.set("onboardCount", count);
    }
    
    public LiveData<Integer> getOnboardCount() {
        return savedStateHandle.getLiveData("onboardCount", 0);
    }
}
