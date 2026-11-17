package com.homeinspect.app.i18n;

import com.homeinspect.app.user.UserProfileService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.LocaleResolver;

/** Bilingual interface (English / Spanish). Reports (PDF and preview) are always in English. */
@Configuration(proxyBeanMethods = false)
class LocaleConfig {

    @Bean(DispatcherServlet.LOCALE_RESOLVER_BEAN_NAME)
    LocaleResolver localeResolver(UserProfileService profiles) {
        return new UserLocaleResolver(profiles);
    }
}
