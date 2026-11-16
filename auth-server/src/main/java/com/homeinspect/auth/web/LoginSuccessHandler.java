package com.homeinspect.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.WebAttributes;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * After the password: continues with the pending steps (two-step code, password change...) or
 * resumes the original request, which is kept meanwhile.
 */
@Component
@RequiredArgsConstructor
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final LoginFlow flow;
    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        request.getSession().removeAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
        flow.startLogin(request.getSession());
        redirectStrategy.sendRedirect(request, response, flow.proceed(request, response, authentication.getName()));
    }
}
