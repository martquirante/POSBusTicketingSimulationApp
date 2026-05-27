package com.buscorp.employee.core.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface QueueDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void enqueueTicket(TicketQueueEntity ticket);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void enqueueAction(ActionQueueEntity action);

    @Query("SELECT * FROM ticket_queue WHERE status != 'synced' ORDER BY createdAt ASC")
    List<TicketQueueEntity> pendingTickets();

    @Query("SELECT * FROM action_queue ORDER BY createdAt ASC")
    List<ActionQueueEntity> pendingActions();

    @Query("UPDATE ticket_queue SET status = :status, retryCount = retryCount + 1 WHERE localId = :localId")
    void updateTicketStatus(String localId, String status);
}
