package com.homeinspect.auth.web;

import com.homeinspect.auth.user.AuthUserService;
import com.homeinspect.auth.user.PasswordPolicy;
import com.homeinspect.auth.user.UserException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/** Login page, landing page and password change. */
@Controller
@RequiredArgsConstructor
class AuthPagesController {

    private final AuthUserService users;
    private final RequestCache requestCache = new HttpSessionRequestCache();

    @GetMapping("/login")
    String login() {
        return "login";
    }

    @GetMapping("/")
    String home() {
        return "index";
    }

    @GetMapping(PasswordChangeRequiredFilter.CHANGE_PATH)
    String changePasswordForm(@ModelAttribute("form") ChangePasswordForm form, HttpSession session, Model model) {
        model.addAttribute("required", isRequired(session));
        model.addAttribute("minLength", PasswordPolicy.MIN_LENGTH);
        return "password-change";
    }

    @PostMapping(PasswordChangeRequiredFilter.CHANGE_PATH)
    String changePassword(@Valid @ModelAttribute("form") ChangePasswordForm form, BindingResult result,
                          Authentication authentication, HttpServletRequest request, HttpServletResponse response,
                          Model model) {
        HttpSession session = request.getSession();
        model.addAttribute("required", isRequired(session));
        model.addAttribute("minLength", PasswordPolicy.MIN_LENGTH);
        if (!result.hasErrors() && !form.getNewPassword().equals(form.getConfirmPassword())) {
            result.rejectValue("confirmPassword", "password.error.mismatch");
        }
        if (result.hasErrors()) {
            return "password-change";
        }
        try {
            users.changePassword(authentication.getName(), form.getCurrentPassword(), form.getNewPassword());
        } catch (UserException e) {
            String field = "password.error.current".equals(e.code()) ? "currentPassword" : "newPassword";
            result.rejectValue(field, e.code());
            return "password-change";
        }
        session.removeAttribute(PasswordChangeRequiredFilter.SESSION_ATTRIBUTE);
        // Resume the original request (e.g. the OAuth2 authorization of the app)
        SavedRequest saved = requestCache.getRequest(request, response);
        if (saved != null) {
            requestCache.removeRequest(request, response);
            return "redirect:" + saved.getRedirectUrl();
        }
        return "redirect:/?passwordChanged";
    }

    private static boolean isRequired(HttpSession session) {
        return Boolean.TRUE.equals(session.getAttribute(PasswordChangeRequiredFilter.SESSION_ATTRIBUTE));
    }
}
