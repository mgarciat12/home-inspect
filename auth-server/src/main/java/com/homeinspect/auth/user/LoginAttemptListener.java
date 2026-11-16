package com.homeinspect.auth.user;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/** Counts failed form logins and locks the account temporarily (see {@link AuthUserService#loginFailed}). */
@Component
@RequiredArgsConstructor
class LoginAttemptListener {

    private final AuthUserService users;

    @EventListener
    void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        if (event.getAuthentication() instanceof UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof String username) {
            users.loginFailed(username.trim());
        }
    }

    @EventListener
    void onSuccess(AuthenticationSuccessEvent event) {
        // Only interactive user logins: client authentication and JWTs also publish success events
        if (event.getAuthentication() instanceof UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof org.springframework.security.core.userdetails.UserDetails details) {
            users.loginSucceeded(details.getUsername());
        }
    }
}
