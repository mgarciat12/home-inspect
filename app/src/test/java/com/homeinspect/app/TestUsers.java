package com.homeinspect.app;

import com.homeinspect.app.security.CurrentUser;
import com.homeinspect.app.user.Role;
import com.homeinspect.app.user.UserProfile;
import com.homeinspect.app.user.UserProfileService;
import com.homeinspect.app.user.UserProfileService.LoginData;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;

/** Creates local profiles and the matching OIDC logins for MockMvc and service tests. */
@Component
public class TestUsers {

    private final UserProfileService profiles;

    public TestUsers(UserProfileService profiles) {
        this.profiles = profiles;
    }

    public UserProfile profile(String username, Role role) {
        return profiles.syncFromLogin(new LoginData(username, username, "Name " + username,
                username + "@example.test", role));
    }

    public OidcLoginRequestPostProcessor login(String username, Role role) {
        profile(username, role);
        return oidcLogin()
                .idToken(token -> token.subject(username).claim("name", "Name " + username)
                        .claim("roles", List.of(role.name())))
                .authorities(new SimpleGrantedAuthority(role.authority()));
    }

    public CurrentUser current(String username, Role role) {
        UserProfile profile = profile(username, role);
        return new CurrentUser(profile.getId(), profile.getSubject(), profile.getDisplayName(), role);
    }

    /** Sets the security context, for service tests that use CurrentUserProvider or method security. */
    public void authenticate(String username, Role role) {
        profile(username, role);
        OidcIdToken idToken = new OidcIdToken("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("sub", username, "name", "Name " + username));
        DefaultOidcUser user = new DefaultOidcUser(List.of(new SimpleGrantedAuthority(role.authority())), idToken);
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority(role.authority()))));
    }
}
