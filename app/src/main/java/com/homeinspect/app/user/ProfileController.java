package com.homeinspect.app.user;

import com.homeinspect.app.photo.ImageRejectedException;
import com.homeinspect.app.security.CurrentUser;
import com.homeinspect.app.security.CurrentUserProvider;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;

/** "My profile": account data (managed in the auth-server) and signature image. */
@Controller
@RequestMapping("/profile")
@RequiredArgsConstructor
class ProfileController {

    private final UserProfileService profiles;
    private final SignatureService signatures;
    private final CurrentUserProvider currentUser;
    private final MessageSource messages;

    @Value("${spring.security.oauth2.client.provider.home-inspect.issuer-uri}")
    private String authServerUrl;

    /** View of the profile page (no entity in the template). */
    record ProfileView(String username, String displayName, String email, Role role, boolean hasSignature) {
    }

    @GetMapping
    String profile(Model model) {
        CurrentUser user = currentUser.get();
        UserProfile profile = profiles.get(user.profileId());
        model.addAttribute("profile", new ProfileView(profile.getUsername(), profile.getDisplayName(),
                profile.getEmail(), profile.getRole(), signatures.file(user.profileId()).isPresent()));
        model.addAttribute("passwordUrl", authServerUrl + "/password/change");
        return "profile";
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
