package com.homeinspect.app.security;

import com.homeinspect.app.user.UserProfile;
import com.homeinspect.app.user.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

/** Resolves the {@link CurrentUser} from the security context. */
@Component
@RequiredArgsConstructor
public class CurrentUserProvider {

    private final UserProfileService profiles;

    public CurrentUser get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
            throw new AuthenticationCredentialsNotFoundException("No logged-in user");
        }
        UserProfile profile = profiles.findBySubject(oidcUser.getSubject())
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("No profile for the user"));
        return new CurrentUser(profile.getId(), profile.getSubject(), profile.getDisplayName(), profile.getRole());
    }
}
