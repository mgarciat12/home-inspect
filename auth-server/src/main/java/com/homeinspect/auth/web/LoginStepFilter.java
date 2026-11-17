package com.homeinspect.auth.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * While the session has a pending {@link LoginStep} (two-step code, temporary password...), every
 * request except that step's page and static resources is redirected to it. Runs before Spring
 * Security, so it also blocks the OAuth2 authorization endpoint: no code is issued to the app
 * until the login is complete.
 */
public class LoginStepFilter extends OncePerRequestFilter {

    public static final String SESSION_ATTRIBUTE = LoginStepFilter.class.getName() + ".STEP";

    private static final List<String> ALWAYS_ALLOWED =
            List.of("/logout", "/css/", "/js/", "/webjars/", "/error", "/actuator/", "/favicon.ico", "/apple-touch-icon");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(SESSION_ATTRIBUTE) instanceof LoginStep step) {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            boolean allowed = path.startsWith(step.path()) || ALWAYS_ALLOWED.stream().anyMatch(path::startsWith);
            if (!allowed) {
                response.sendRedirect(request.getContextPath() + step.path());
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
