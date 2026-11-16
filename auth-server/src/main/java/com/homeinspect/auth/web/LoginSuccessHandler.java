package com.homeinspect.auth.web;

import com.homeinspect.auth.user.AuthUser;
import com.homeinspect.auth.user.AuthUserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * After a successful login, users with a temporary password are sent to the password change page;
 * the original request (usually the OAuth2 authorization request) is kept and resumed afterwards.
 */
@Component
public class LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final AuthUserService users;

    LoginSuccessHandler(AuthUserService users) {
        this.users = users;
        setDefaultTargetUrl("/");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws ServletException, IOException {
        boolean mustChange = users.findByUsername(authentication.getName())
                .map(AuthUser::isMustChangePassword)
                .orElse(false);
        if (mustChange) {
            request.getSession().setAttribute(PasswordChangeRequiredFilter.SESSION_ATTRIBUTE, Boolean.TRUE);
            clearAuthenticationAttributes(request);
            getRedirectStrategy().sendRedirect(request, response, PasswordChangeRequiredFilter.CHANGE_PATH);
            return;
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
