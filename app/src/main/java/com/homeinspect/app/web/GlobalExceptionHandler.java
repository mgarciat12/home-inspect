package com.homeinspect.app.web;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

/** Upload too large: JSON for the editor (fetch), redirect with a message for normal forms. */
@ControllerAdvice
@RequiredArgsConstructor
class GlobalExceptionHandler {

    private final MessageSource messages;

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    Object uploadTooLarge(HttpServletRequest request, RedirectAttributes redirect) {
        String message = messages.getMessage("photo.error.tooLarge", null, LocaleContextHolder.getLocale());
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(Map.of("photos", List.of(), "errors", List.of(Map.of("file", "", "message", message))));
        }
        redirect.addFlashAttribute("error", message);
        return "redirect:" + sameSitePath(request.getHeader("Referer"));
    }

    /** Path of the page that sent the form (never an external URL). */
    private static String sameSitePath(String referer) {
        if (referer == null) {
            return "/";
        }
        try {
            java.net.URI uri = java.net.URI.create(referer);
            String path = uri.getRawPath();
            if (path == null || !path.startsWith("/") || path.startsWith("//")) {
                return "/";
            }
            return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
        } catch (IllegalArgumentException e) {
            return "/";
        }
    }
}
