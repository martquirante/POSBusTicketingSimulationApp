package com.buscorp.employee.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface TicketDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertTicket(TicketEntity ticket);

    @Update
    void updateTicket(TicketEntity ticket);

    @Query("SELECT * FROM tickets WHERE is_synced = 0 ORDER BY timestamp ASC")
    List<TicketEntity> getUnsyncedTickets();

    @Query("SELECT * FROM tickets ORDER BY timestamp DESC LIMIT 100")
    List<TicketEntity> getRecentTickets();

    @Query("UPDATE tickets SET is_synced = 1 WHERE ticket_id = :ticketId")
    void markAsSynced(String ticketId);
}
