package com.buscorp.employee.core.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "cached_routes")
public class CachedRouteEntity {
    @PrimaryKey
    @NonNull
    public String routeId;
    public String payloadJson;
    public long updatedAt;

    public CachedRouteEntity(@NonNull String routeId, String payloadJson, long updatedAt) {
        this.routeId = routeId;
        this.payloadJson = payloadJson;
        this.updatedAt = updatedAt;
    }
}
