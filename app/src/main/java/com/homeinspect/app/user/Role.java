package com.homeinspect.app.user;

public enum Role {
    ADMIN,
    INSPECTOR;

    public String authority() {
        return "ROLE_" + name();
    }
}
