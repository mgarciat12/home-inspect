package com.homeinspect.app.security;

import com.homeinspect.app.user.Role;
import com.homeinspect.app.user.UserProfileService;
import com.homeinspect.app.user.UserProfileService.LoginData;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Maps the {@code roles} claim of the ID token to {@code ROLE_ADMIN} / {@code ROLE_INSPECTOR} and
 * creates or updates the local profile of the user at every login.
 */
@Component
@RequiredArgsConstructor
public class ProfileSyncingOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    public static final String ROLES_CLAIM = "roles";

    private final OidcUserService delegate = new OidcUserService();
    private final UserProfileService profiles;

    @Override
    public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
        OidcUser oidcUser = delegate.loadUser(request);
        Role role = mainRole(oidcUser.getIdToken().getClaim(ROLES_CLAIM))
                .orElseThrow(() -> new OAuth2AuthenticationException(
                        new OAuth2Error("access_denied", "The user has no home-inspect role", null)));

        profiles.syncFromLogin(new LoginData(oidcUser.getSubject(), oidcUser.getPreferredUsername(),
                oidcUser.getFullName(), oidcUser.getEmail(), role));

        Set<GrantedAuthority> authorities = new LinkedHashSet<>(oidcUser.getAuthorities());
        authorities.add(new SimpleGrantedAuthority(role.authority()));
        return new DefaultOidcUser(authorities, oidcUser.getIdToken(), oidcUser.getUserInfo(), IdTokenClaimNames.SUB);
    }

    /** ADMIN wins over INSPECTOR; unknown values are ignored. */
    static Optional<Role> mainRole(Object claim) {
        if (!(claim instanceof Collection<?> values)) {
            return Optional.empty();
        }
        List<String> names = values.stream().map(String::valueOf).toList();
        if (names.contains(Role.ADMIN.name())) {
            return Optional.of(Role.ADMIN);
        }
        if (names.contains(Role.INSPECTOR.name())) {
            return Optional.of(Role.INSPECTOR);
        }
        return Optional.empty();
    }
}
