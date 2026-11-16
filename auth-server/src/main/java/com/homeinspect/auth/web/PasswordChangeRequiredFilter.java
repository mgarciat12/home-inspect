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
 * While the session is marked with {@link #SESSION_ATTRIBUTE} (temporary password), every request
 * except the password change page and static resources is redirected there. This also blocks the
 * OAuth2 authorization endpoint, so no code is issued until the password is changed.
 */
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    public static final String SESSION_ATTRIBUTE = PasswordChangeRequiredFilter.class.getName() + ".REQUIRED";
    public static final String CHANGE_PATH = "/password/change";

    private static final List<String> ALLOWED_PREFIXES =
            List.of(CHANGE_PATH, "/logout", "/css/", "/js/", "/webjars/", "/error", "/actuator/", "/favicon.ico");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && Boolean.TRUE.equals(session.getAttribute(SESSION_ATTRIBUTE))) {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            if (ALLOWED_PREFIXES.stream().noneMatch(path::startsWith)) {
                response.sendRedirect(request.getContextPath() + CHANGE_PATH);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
