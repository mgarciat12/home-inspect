package com.homeinspect.auth.mfa;

import com.homeinspect.auth.user.AuthUserService;
import com.homeinspect.auth.web.LoginFlow;
import com.homeinspect.auth.web.LoginStep;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/** Two-step verification pages: code at login, setup with QR code, and disabling it. */
@Controller
@RequiredArgsConstructor
class MfaController {

    static final String PENDING_SECRET = MfaController.class.getName() + ".PENDING_SECRET";

    private final TotpService totp;
    private final LoginFlow flow;
    private final AuthUserService users;
    private final PasswordEncoder passwordEncoder;

    // ---- code at login ----

    @GetMapping("/mfa/verify")
    String verifyForm(HttpSession session) {
        if (flow.pendingStep(session) != LoginStep.MFA_VERIFY) {
            return "redirect:/";
        }
        return "mfa-verify";
    }

    @PostMapping("/mfa/verify")
    String verify(@RequestParam String code, Authentication authentication, HttpServletRequest request,
                  HttpServletResponse response, Model model) {
        HttpSession session = request.getSession();
        if (flow.pendingStep(session) != LoginStep.MFA_VERIFY) {
            return "redirect:/";
        }
        String username = authentication.getName();
        if (totp.verify(username, code)) {
            users.loginSucceeded(username);
            flow.markMfaVerified(session);
            return "redirect:" + flow.proceed(request, response, username);
        }
        // Wrong codes count as failed logins: after too many the account is locked and the session ends
        users.loginFailed(username);
        if (users.findByUsername(username).map(u -> u.isLocked(java.time.Instant.now())).orElse(true)) {
            SecurityContextHolder.clearContext();
            session.invalidate();
            return "redirect:/login?locked";
        }
        model.addAttribute("error", true);
        return "mfa-verify";
    }

    // ---- setup ----

    @GetMapping("/mfa/setup")
    String setupForm(Authentication authentication, HttpSession session, Model model) {
        requireActive();
        if (totp.isEnabled(authentication.getName())) {
            return "redirect:/";
        }
        String secret = pendingSecret(session);
        model.addAttribute("secret", secret.replaceAll("(.{4})", "$1 ").trim());
        model.addAttribute("required", flow.pendingStep(session) == LoginStep.MFA_SETUP);
        return "mfa-setup";
    }

    @GetMapping("/mfa/setup/qr")
    ResponseEntity<byte[]> qr(Authentication authentication, HttpSession session) {
        requireActive();
        byte[] png = totp.qrPng(totp.otpauthUri(authentication.getName(), pendingSecret(session)));
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore()).body(png);
    }

    @PostMapping("/mfa/setup")
    String setup(@RequestParam String code, Authentication authentication, HttpServletRequest request,
                 HttpServletResponse response, Model model) {
        requireActive();
        HttpSession session = request.getSession();
        String secret = pendingSecret(session);
        if (!totp.enable(authentication.getName(), secret, code)) {
            model.addAttribute("secret", secret.replaceAll("(.{4})", "$1 ").trim());
            model.addAttribute("required", flow.pendingStep(session) == LoginStep.MFA_SETUP);
            model.addAttribute("error", true);
            return "mfa-setup";
        }
        session.removeAttribute(PENDING_SECRET);
        flow.markMfaVerified(session);
        if (flow.pendingStep(session) != null) {
            return "redirect:" + flow.proceed(request, response, authentication.getName());
        }
        return "redirect:/?mfaEnabled";
    }

    // ---- disable ----

    @PostMapping("/mfa/disable")
    String disable(@RequestParam String password, @RequestParam String code, Authentication authentication) {
        requireActive();
        String username = authentication.getName();
        boolean passwordOk = users.findByUsername(username)
                .map(u -> passwordEncoder.matches(password, u.getPasswordHash())).orElse(false);
        if (totp.required() || !passwordOk || !totp.verify(username, code)) {
            return "redirect:/?mfaError";
        }
        totp.disable(username);
        return "redirect:/?mfaDisabled";
    }

    private void requireActive() {
        if (!totp.active()) {
            throw new ResponseStatusException(NOT_FOUND);
        }
    }

    private static String pendingSecret(HttpSession session) {
        Object secret = session.getAttribute(PENDING_SECRET);
        if (secret instanceof String value) {
            return value;
        }
        String created = Totp.newSecret();
        session.setAttribute(PENDING_SECRET, created);
        return created;
    }
}
