package com.homeinspect.app.security;

import com.homeinspect.app.user.UserProfile;
import com.homeinspect.app.user.UserProfileService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Ends the session of a user that has been disabled, or whose role has changed, in the admin panel
 * since they logged in. They are sent through the login again, which applies the new state.
 */
public class ActiveUserFilter extends OncePerRequestFilter {

    private final UserProfileService profiles;

    public ActiveUserFilter(UserProfileService profiles) {
        this.profiles = profiles;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof OidcUser oidcUser) {
            Optional<UserProfile> profile = profiles.findBySubject(oidcUser.getSubject());
            boolean stale = profile.isEmpty()
                    || !profile.get().isActive()
                    || authentication.getAuthorities().stream()
                    .noneMatch(authority -> authority.getAuthority().equals(profile.get().getRole().authority()));
            if (stale) {
                SecurityContextHolder.clearContext();
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                response.sendRedirect(request.getContextPath() + "/");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
