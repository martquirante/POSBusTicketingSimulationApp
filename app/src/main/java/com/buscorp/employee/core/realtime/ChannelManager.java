package com.buscorp.employee.core.realtime;

import com.buscorp.employee.core.auth.SessionManager;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ChannelManager {

    private final SupabaseRealtimeClient realtimeClient;
    private final SessionManager sessionManager;

    @Inject
    public ChannelManager(SupabaseRealtimeClient realtimeClient, SessionManager sessionManager) {
        this.realtimeClient = realtimeClient;
        this.sessionManager = sessionManager;
    }

    public void subscribeConductorChannels(String busId, SupabaseRealtimeClient.RealtimeListener listener) {
        String userId = sessionManager.getUserId();
        if (userId != null) {
            realtimeClient.subscribePostgresChanges(
                    "realtime:public:notifications:user_id=eq." + userId,
                    "notifications",
                    "user_id=eq." + userId,
                    listener
            );
            realtimeClient.subscribePostgresChanges(
                    "realtime:public:tickets:conductor_id=eq." + userId,
                    "tickets",
                    "conductor_id=eq." + userId,
                    listener
            );
        }

        if (busId != null && !busId.isEmpty()) {
            realtimeClient.subscribePostgresChanges(
                    "realtime:public:trips:bus_id=eq." + busId,
                    "trips",
                    "bus_id=eq." + busId,
                    listener
            );
            realtimeClient.subscribePostgresChanges(
                    "realtime:public:route_change_requests:bus_id=eq." + busId,
                    "route_change_requests",
                    "bus_id=eq." + busId,
                    listener
            );
        }
    }
}
