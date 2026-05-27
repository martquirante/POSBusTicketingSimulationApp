package com.buscorp.employee.core.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Query;

public interface SupabaseApi {

    @POST("auth/v1/token?grant_type=password")
    Call<JsonObject> passwordLogin(@Body JsonObject body);

    @GET("auth/v1/user")
    Call<JsonObject> getUser();

    @POST("auth/v1/logout")
    Call<Void> logout();

    @PUT("auth/v1/user")
    Call<JsonObject> updateUser(@Body JsonObject body);

    @POST("rest/v1/rpc/has_role")
    Call<Boolean> hasRole(@Body JsonObject body);

    @GET("rest/v1/profiles")
    Call<JsonArray> getProfileGate(
            @Query("id") String userFilter,
            @Query("select") String select
    );

    default Call<JsonArray> getProfileGate(String userFilter) {
        return getProfileGate(userFilter, "id,must_change_password");
    }

    @Headers({
            "Prefer: resolution=merge-duplicates",
            "Prefer: return=minimal"
    })
    @POST("rest/v1/fcm_tokens")
    Call<Void> upsertFcmToken(@Body JsonObject body);

    @Headers({
            "Prefer: return=representation"
    })
    @PATCH("rest/v1/tickets")
    Call<JsonArray> verifyTicket(
            @Query("id") String ticketId,
            @Query("id") String eq,
            @Body JsonObject body
    );
}
