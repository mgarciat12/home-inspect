package com.homeinspect.auth.user;

public enum Role {
    ADMIN,
    INSPECTOR;

    public String authority() {
        return "ROLE_" + name();
    }
}
