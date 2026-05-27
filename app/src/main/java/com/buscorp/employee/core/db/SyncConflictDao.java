package com.buscorp.employee.core.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface SyncConflictDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertConflict(SyncConflictEntity conflict);

    @Query("SELECT * FROM sync_conflicts")
    List<SyncConflictEntity> getAllConflicts();

    @Query("SELECT COUNT(*) FROM sync_conflicts")
    LiveData<Integer> getConflictCountLive();

    @Query("DELETE FROM sync_conflicts WHERE id = :id")
    void deleteConflict(String id);
}
