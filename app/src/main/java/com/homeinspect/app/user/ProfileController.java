package com.homeinspect.app.user;

import com.homeinspect.app.i18n.UserLocaleResolver;
import com.homeinspect.app.photo.ImageRejectedException;
import com.homeinspect.app.security.CurrentUser;
import com.homeinspect.app.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.Locale;

/** "My profile": account data (managed in the auth-server) and signature image. */
@Controller
@RequestMapping("/profile")
@RequiredArgsConstructor
class ProfileController {

    private final UserProfileService profiles;
    private final SignatureService signatures;
    private final LicenseService licenses;
    private final LocaleResolver localeResolver;
    private final CurrentUserProvider currentUser;
    private final MessageSource messages;

    @Value("${spring.security.oauth2.client.provider.home-inspect.issuer-uri}")
    private String authServerUrl;

    /** View of the profile page (no entity in the template). */
    record ProfileView(String username, String displayName, String email, Role role, boolean hasSignature,
                       String language) {
    }

    @GetMapping
    String profile(Model model) {
        CurrentUser user = currentUser.get();
        UserProfile profile = profiles.get(user.profileId());
        model.addAttribute("profile", new ProfileView(profile.getUsername(), profile.getDisplayName(),
                profile.getEmail(), profile.getRole(), signatures.file(user.profileId()).isPresent(),
                profile.getLanguage()));
        model.addAttribute("licenses", licenses.list(user.profileId()));
        model.addAttribute("licenseTypes", LicenseType.values());
        model.addAttribute("maxLicenses", LicenseService.MAX_LICENSES);
        model.addAttribute("passwordUrl", authServerUrl + "/password/change");
        return "profile";
    }

    /** Interface language: "en", "es" or empty to follow the browser. */
    @PostMapping("/language")
    String language(@RequestParam(defaultValue = "") String language, HttpServletRequest request,
                    HttpServletResponse response, RedirectAttributes redirect) {
        String code = UserLocaleResolver.normalize(language);
        profiles.setLanguage(currentUser.get().profileId(), code);
        localeResolver.setLocale(request, response, code == null ? null : Locale.of(code));
        // The message is shown in the new language
        LocaleContextHolder.setLocale(localeResolver.resolveLocale(request));
        redirect.addFlashAttribute("message", message("profile.language.saved"));
        return "redirect:/profile";
    }

    @PostMapping("/licenses")
    String addLicense(@RequestParam(required = false) LicenseType type, @RequestParam(defaultValue = "") String number,
                      RedirectAttributes redirect) {
        try {
            licenses.add(currentUser.get().profileId(), type, number);
            redirect.addFlashAttribute("message", message("profile.license.added"));
        } catch (LicenseService.InvalidLicenseException e) {
            redirect.addFlashAttribute("error", message(e.code()));
        }
        return "redirect:/profile#licenses";
    }

    @PostMapping("/licenses/{id}/delete")
    String deleteLicense(@PathVariable long id, RedirectAttributes redirect) {
        licenses.delete(currentUser.get().profileId(), id);
        redirect.addFlashAttribute("message", message("profile.license.deleted"));
        return "redirect:/profile#licenses";
    }

    @PostMapping("/signature")
    String uploadSignature(@RequestParam("file") MultipartFile file, RedirectAttributes redirect) {
        try {
            signatures.save(currentUser.get().profileId(), file.getBytes());
            redirect.addFlashAttribute("message", message("profile.signature.saved"));
        } catch (ImageRejectedException e) {
            redirect.addFlashAttribute("error", message(e.code()));
        } catch (IOException e) {
            redirect.addFlashAttribute("error", message("photo.error.invalid"));
        }
        return "redirect:/profile";
    }

    @PostMapping("/signature/delete")
    String deleteSignature(RedirectAttributes redirect) {
        signatures.delete(currentUser.get().profileId());
        redirect.addFlashAttribute("message", message("profile.signature.deleted"));
        return "redirect:/profile";
    }

    @GetMapping("/signature")
    ResponseEntity<Resource> signature() {
        return signatures.file(currentUser.get().profileId())
                .map(path -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_PNG)
                        .cacheControl(CacheControl.noCache().cachePrivate())
                        .<Resource>body(new FileSystemResource(path)))
                .orElse(ResponseEntity.notFound().build());
    }

    private String message(String code) {
        return messages.getMessage(code, null, code, LocaleContextHolder.getLocale());
    }
}
