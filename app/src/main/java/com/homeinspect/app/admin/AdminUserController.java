package com.homeinspect.app.admin;

import com.homeinspect.app.admin.AuthServerUsersClient.NewUser;
import com.homeinspect.app.admin.AuthServerUsersClient.RemoteUser;
import com.homeinspect.app.admin.AuthServerUsersClient.RemoteUserWithPassword;
import com.homeinspect.app.admin.AuthServerUsersClient.UserChanges;
import com.homeinspect.app.security.CurrentUserProvider;
import com.homeinspect.app.user.Role;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Admin panel: users of the application (accounts managed in the auth-server). */
@Controller
@RequestMapping("/admin/users")
@RequiredArgsConstructor
class AdminUserController {

    private final AdminUserService users;
    private final CurrentUserProvider currentUser;
    private final MessageSource messages;

    @ModelAttribute("roles")
    Role[] roles() {
        return Role.values();
    }

    @GetMapping
    String list(Model model) {
        try {
            model.addAttribute("users", users.list());
        } catch (AdminApiException e) {
            model.addAttribute("users", java.util.List.of());
            model.addAttribute("error", message(e.code()));
        }
        model.addAttribute("me", currentUser.get().subject());
        return "admin/users";
    }

    @GetMapping("/new")
    String newForm(@ModelAttribute("form") AdminUserForm form) {
        return "admin/user-form";
    }

    @PostMapping
    String create(@Valid @ModelAttribute("form") AdminUserForm form, BindingResult result, Model model,
                  RedirectAttributes redirect) {
        if (form.getUsername() == null || form.getUsername().isBlank()) {
            result.rejectValue("username", "NotBlank");
        }
        if (result.hasErrors()) {
            return "admin/user-form";
        }
        try {
            RemoteUserWithPassword created = users.create(
                    new NewUser(form.getUsername(), form.getDisplayName(), form.getEmail(), form.getRole()));
            temporaryPassword(redirect, created, "admin.user.created");
            return "redirect:/admin/users";
        } catch (AdminApiException e) {
            model.addAttribute("error", message(e.code()));
            return "admin/user-form";
        }
    }

    @GetMapping("/{id}/edit")
    String editForm(@PathVariable long id, Model model) {
        RemoteUser user = users.get(id);
        AdminUserForm form = new AdminUserForm();
        form.setUsername(user.username());
        form.setDisplayName(user.displayName());
        form.setEmail(user.email());
        form.setRole(user.role());
        form.setEnabled(user.enabled());
        model.addAttribute("form", form);
        model.addAttribute("user", user);
        return "admin/user-form";
    }

    @PostMapping("/{id}")
    String update(@PathVariable long id, @Valid @ModelAttribute("form") AdminUserForm form, BindingResult result,
                  Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            model.addAttribute("user", users.get(id));
            return "admin/user-form";
        }
        try {
            users.update(id, new UserChanges(form.getDisplayName(), form.getEmail() == null ? "" : form.getEmail(),
                    form.getRole(), form.isEnabled()), currentUser.get());
            redirect.addFlashAttribute("message", message("admin.user.saved"));
            return "redirect:/admin/users";
        } catch (AdminApiException e) {
            model.addAttribute("user", users.get(id));
            model.addAttribute("error", message(e.code()));
            return "admin/user-form";
        }
    }

    @PostMapping("/{id}/enabled")
    String setEnabled(@PathVariable long id, @RequestParam boolean enabled, RedirectAttributes redirect) {
        try {
            users.setEnabled(id, enabled, currentUser.get());
            redirect.addFlashAttribute("message", message(enabled ? "admin.user.enabled" : "admin.user.disabled"));
        } catch (AdminApiException e) {
            redirect.addFlashAttribute("error", message(e.code()));
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/password-reset")
    String resetPassword(@PathVariable long id, RedirectAttributes redirect) {
        try {
            temporaryPassword(redirect, users.resetPassword(id), "admin.user.passwordReset");
        } catch (AdminApiException e) {
            redirect.addFlashAttribute("error", message(e.code()));
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/mfa-reset")
    String resetMfa(@PathVariable long id, RedirectAttributes redirect) {
        try {
            users.resetMfa(id);
            redirect.addFlashAttribute("message", message("admin.user.mfaReset"));
        } catch (AdminApiException e) {
            redirect.addFlashAttribute("error", message(e.code()));
        }
        return "redirect:/admin/users";
    }

    /** API errors while loading a page (e.g. unknown user): back to the list with the message. */
    @org.springframework.web.bind.annotation.ExceptionHandler(AdminApiException.class)
    String apiError(AdminApiException e, RedirectAttributes redirect) {
        redirect.addFlashAttribute("error", message(e.code()));
        return "redirect:/admin/users";
    }

    private void temporaryPassword(RedirectAttributes redirect, RemoteUserWithPassword result, String messageKey) {
        redirect.addFlashAttribute("message", message(messageKey, result.user().username()));
        redirect.addFlashAttribute("temporaryUser", result.user().username());
        redirect.addFlashAttribute("temporaryPassword", result.temporaryPassword());
    }

    private String message(String code, Object... args) {
        return messages.getMessage(code, args, code, LocaleContextHolder.getLocale());
    }
}
