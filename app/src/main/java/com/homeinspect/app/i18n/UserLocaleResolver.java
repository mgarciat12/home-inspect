package com.homeinspect.app.i18n;

import com.homeinspect.app.user.UserProfile;
import com.homeinspect.app.user.UserProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.servlet.LocaleResolver;

import java.util.List;
import java.util.Locale;

/**
 * Interface language: the one chosen in "My profile" or, without a choice, the best match of the
 * browser's Accept-Language among the supported ones (English by default). The choice is read
 * from the profile once and kept in the session.
 */
public class UserLocaleResolver implements LocaleResolver {

    public static final Locale SPANISH = Locale.of("es");
    public static final List<Locale> SUPPORTED = List.of(Locale.ENGLISH, SPANISH);
    /** Language of the reports (PDF and preview): always English, whatever the interface language. */
    public static final Locale REPORT_LOCALE = Locale.US;
    /** Request attribute that forces a locale for one request (e.g. the report preview). */
    public static final String FORCED_LOCALE = UserLocaleResolver.class.getName() + ".FORCED";
    /** Session attribute: language code, or "" when the user follows the browser. */
    static final String SESSION_ATTRIBUTE = UserLocaleResolver.class.getName() + ".LANGUAGE";

    private final UserProfileService profiles;

    public UserLocaleResolver(UserProfileService profiles) {
        this.profiles = profiles;
    }

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        if (request.getAttribute(FORCED_LOCALE) instanceof Locale forced) {
            return forced;
        }
        String language = chosenLanguage(request);
        if (language != null && !language.isEmpty()) {
            return Locale.of(language);
        }
        return browserLocale(request);
    }

    /** Stores the choice of the current request (null = follow the browser). */
    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        request.getSession().setAttribute(SESSION_ATTRIBUTE, locale == null ? "" : locale.getLanguage());
    }

    /** Converts a stored code into a supported language code, or null. */
    public static String normalize(String language) {
        return SUPPORTED.stream().map(Locale::getLanguage).filter(code -> code.equals(language)).findFirst().orElse(null);
    }

    private String chosenLanguage(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(SESSION_ATTRIBUTE) instanceof String cached) {
            return cached;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof OidcUser user)) {
            return null;
        }
        String language = profiles.findBySubject(user.getSubject()).map(UserProfile::getLanguage)
                .map(UserLocaleResolver::normalize).orElse(null);
        request.getSession().setAttribute(SESSION_ATTRIBUTE, language == null ? "" : language);
        return language;
    }

    private static Locale browserLocale(HttpServletRequest request) {
        String header = request.getHeader("Accept-Language");
        if (header == null || header.isBlank()) {
            return Locale.ENGLISH;
        }
        try {
            Locale match = Locale.lookup(Locale.LanguageRange.parse(header), SUPPORTED);
            return match != null ? match : Locale.ENGLISH;
        } catch (IllegalArgumentException e) {
            return Locale.ENGLISH;
        }
    }
}
