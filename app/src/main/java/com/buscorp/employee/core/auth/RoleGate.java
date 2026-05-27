package com.buscorp.employee.core.auth;

public final class RoleGate {
    public static final String ROLE_CONDUCTOR = "conductor";
    public static final String ROLE_DRIVER = "driver";
    public static final String ROLE_INSPECTOR = "inspector";
    public static final String ROLE_MECHANIC = "mechanic";

    private RoleGate() {
    }

    public static boolean isSupportedForThisRelease(String role) {
        return ROLE_CONDUCTOR.equalsIgnoreCase(role);
    }
}
