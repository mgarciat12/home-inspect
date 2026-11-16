package com.homeinspect.auth.web;

import com.homeinspect.auth.mfa.TotpService;
import com.homeinspect.auth.user.AuthUser;
import com.homeinspect.auth.user.AuthUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;

/**
 * Decides what comes after the password: two-step code, password change, two-step setup, or
 * back to the original request (usually the OAuth2 authorization of the app).
 */
@Component
@RequiredArgsConstructor
public class LoginFlow {

    static final String MFA_VERIFIED = LoginFlow.class.getName() + ".MFA_VERIFIED";

    private final AuthUserService users;
    private final TotpService totp;
    private final RequestCache requestCache = new HttpSessionRequestCache();

    /** Called right after the password is accepted. */
    public void startLogin(HttpSession session) {
        session.removeAttribute(MFA_VERIFIED);
        session.removeAttribute(LoginStepFilter.SESSION_ATTRIBUTE);
    }

    public void markMfaVerified(HttpSession session) {
        session.setAttribute(MFA_VERIFIED, Boolean.TRUE);
    }

    /** URL to send the user to now; sets or clears the pending step of the session. */
    public String proceed(HttpServletRequest request, HttpServletResponse response, String username) {
        HttpSession session = request.getSession();
        LoginStep step = nextStep(session, username);
        if (step != null) {
            session.setAttribute(LoginStepFilter.SESSION_ATTRIBUTE, step);
            return step.path();
        }
        session.removeAttribute(LoginStepFilter.SESSION_ATTRIBUTE);
        SavedRequest saved = requestCache.getRequest(request, response);
        if (saved != null) {
            requestCache.removeRequest(request, response);
            return saved.getRedirectUrl();
        }
        return "/";
    }

    public LoginStep pendingStep(HttpSession session) {
        return session.getAttribute(LoginStepFilter.SESSION_ATTRIBUTE) instanceof LoginStep step ? step : null;
    }

    private LoginStep nextStep(HttpSession session, String username) {
        AuthUser user = users.findByUsername(username).orElse(null);
        if (user == null) {
            return null;
        }
        if (totp.active() && user.isTotpEnabled() && session.getAttribute(MFA_VERIFIED) == null) {
            return LoginStep.MFA_VERIFY;
        }
        if (user.isMustChangePassword()) {
            return LoginStep.PASSWORD_CHANGE;
        }
        if (totp.required() && !user.isTotpEnabled()) {
            return LoginStep.MFA_SETUP;
        }
        return null;
    }
}
