package com.buscorp.employee.core.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface AuditDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertPending(PendingAuditEntity entity);

    @Query("SELECT * FROM pending_audit ORDER BY createdAt ASC")
    List<PendingAuditEntity> pendingAudit();
}
