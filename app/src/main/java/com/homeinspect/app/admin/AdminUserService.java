package com.homeinspect.app.admin;

import com.homeinspect.app.admin.AuthServerUsersClient.NewUser;
import com.homeinspect.app.admin.AuthServerUsersClient.RemoteUser;
import com.homeinspect.app.admin.AuthServerUsersClient.RemoteUserWithPassword;
import com.homeinspect.app.admin.AuthServerUsersClient.UserChanges;
import com.homeinspect.app.security.CurrentUser;
import com.homeinspect.app.user.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * User administration: the accounts live in the auth-server (reached through its API); the local
 * profiles are updated right away so that the change applies to open sessions.
 */
@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserService {

    private final AuthServerUsersClient client;
    private final UserProfileService profiles;

    public List<RemoteUser> list() {
        return client.list();
    }

    public RemoteUser get(long id) {
        return client.get(id);
    }

    public RemoteUserWithPassword create(NewUser user) {
        return client.create(user);
    }

    public RemoteUser update(long id, UserChanges changes, CurrentUser admin) {
        RemoteUser current = client.get(id);
        if (isSelf(current, admin) && (Boolean.FALSE.equals(changes.enabled())
                || (changes.role() != null && changes.role() != current.role()))) {
            throw new AdminApiException(null, "admin.error.self", null);
        }
        RemoteUser updated = client.update(id, changes);
        profiles.mirrorAccountChange(updated.username(), updated.enabled(), updated.role(),
                updated.displayName(), updated.email() == null ? "" : updated.email());
        return updated;
    }

    public RemoteUser setEnabled(long id, boolean enabled, CurrentUser admin) {
        RemoteUser current = client.get(id);
        if (isSelf(current, admin) && !enabled) {
            throw new AdminApiException(null, "admin.error.self", null);
        }
        RemoteUser updated = client.setEnabled(id, enabled);
        profiles.mirrorAccountChange(updated.username(), updated.enabled(), null, null, null);
        return updated;
    }

    public RemoteUserWithPassword resetPassword(long id) {
        return client.resetPassword(id);
    }

    private static boolean isSelf(RemoteUser user, CurrentUser admin) {
        return user.username().equals(admin.subject());
    }
}
