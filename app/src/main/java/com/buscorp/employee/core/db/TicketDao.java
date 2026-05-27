package com.buscorp.employee.core.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface TicketDao {

    @Insert
    void insert(TicketEntity ticket);

    @androidx.room.Delete
    void delete(TicketEntity ticket);

    @Query("SELECT * FROM tickets ORDER BY timestamp DESC")
    List<TicketEntity> getAllTickets();

    @Query("SELECT * FROM tickets WHERE is_synced = 0 ORDER BY timestamp ASC")
    List<TicketEntity> getUnsyncedTickets();

    @Update
    void update(TicketEntity ticket);
    
    @Query("UPDATE tickets SET is_synced = 1 WHERE id IN (:ticketIds)")
    void markAsSynced(List<String> ticketIds);
}
