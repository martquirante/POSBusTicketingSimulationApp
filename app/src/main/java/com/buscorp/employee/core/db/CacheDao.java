package com.buscorp.employee.core.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface CacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertRoute(CachedRouteEntity route);

    @Query("SELECT * FROM cached_routes ORDER BY updatedAt DESC")
    List<CachedRouteEntity> routes();
}
