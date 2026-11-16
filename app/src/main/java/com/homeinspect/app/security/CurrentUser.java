package com.homeinspect.app.security;

import com.homeinspect.app.user.Role;

/** The logged-in user, as seen by the service layer (access checks are done with it). */
public record CurrentUser(long profileId, String subject, String displayName, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
