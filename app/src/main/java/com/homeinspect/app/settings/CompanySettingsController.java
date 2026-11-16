package com.homeinspect.app.settings;

import com.homeinspect.app.photo.ImageRejectedException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;

/** Admin settings: company data, logo and standard texts of the reports. */
@Controller
@RequiredArgsConstructor
class CompanySettingsController {

    private final CompanySettingsService settings;
    private final MessageSource messages;

    @GetMapping("/admin/settings")
    String form(Model model) {
        model.addAttribute("form", settings.form());
        model.addAttribute("hasLogo", settings.logoFile().isPresent());
        return "admin/settings";
    }

    @PostMapping("/admin/settings")
    String save(@Valid @ModelAttribute("form") CompanySettingsForm form, BindingResult result, Model model,
                RedirectAttributes redirect) {
        if (result.hasErrors()) {
            model.addAttribute("hasLogo", settings.logoFile().isPresent());
            return "admin/settings";
        }
        settings.update(form);
        redirect.addFlashAttribute("message", message("settings.saved"));
        return "redirect:/admin/settings";
    }

    @PostMapping("/admin/settings/logo")
    String uploadLogo(@RequestParam("file") MultipartFile file, RedirectAttributes redirect) {
        try {
            settings.saveLogo(file.getBytes());
            redirect.addFlashAttribute("message", message("settings.logo.saved"));
        } catch (ImageRejectedException e) {
            redirect.addFlashAttribute("error", message(e.code()));
        } catch (IOException e) {
            redirect.addFlashAttribute("error", message("photo.error.invalid"));
        }
        return "redirect:/admin/settings";
    }

    @PostMapping("/admin/settings/logo/delete")
    String deleteLogo(RedirectAttributes redirect) {
        settings.deleteLogo();
        redirect.addFlashAttribute("message", message("settings.logo.deleted"));
        return "redirect:/admin/settings";
    }

    /** Logo for any logged-in user (preview of the reports). */
    @GetMapping("/settings/logo")
    ResponseEntity<Resource> logo() {
        return settings.logoFile()
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
