package com.homeinspect.app.config;

import com.homeinspect.app.security.ActiveUserFilter;
import com.homeinspect.app.security.ProfileSyncingOidcUserService;
import com.homeinspect.app.user.UserProfileService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

import java.util.Set;

/**
 * The whole application requires a login through the auth-server (OIDC, authorization code + PKCE).
 * Logout ends both the local session and the one in the authorization server.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig {

    static final String REGISTRATION_ID = "home-inspect";

    @Bean
    SecurityFilterChain webFilterChain(HttpSecurity http, ClientRegistrationRepository clients,
                                       ProfileSyncingOidcUserService oidcUserService, UserProfileService profiles) {
        DefaultOAuth2AuthorizationRequestResolver authorizationRequestResolver =
                new DefaultOAuth2AuthorizationRequestResolver(clients,
                        OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
        // PKCE also for this confidential client (required by the auth-server)
        authorizationRequestResolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());

        OidcClientInitiatedLogoutSuccessHandler logoutSuccessHandler = new OidcClientInitiatedLogoutSuccessHandler(clients);
        logoutSuccessHandler.setPostLogoutRedirectUri("{baseUrl}/");

        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/css/**", "/js/**", "/img/**", "/webjars/**", "/favicon.ico", "/apple-touch-icon*.png", "/error", "/login-error").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .requestCache(cache -> cache.requestCache(pageRequestCache()))
                .oauth2Login(login -> login
                        .loginPage("/oauth2/authorization/" + REGISTRATION_ID)
                        .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(authorizationRequestResolver))
                        .userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUserService))
                        .defaultSuccessUrl("/", false)
                        // Not the login page itself: it would loop back to the auth-server
                        .failureUrl("/login-error"))
                .logout(logout -> logout
                        .logoutSuccessHandler(logoutSuccessHandler)
                        .deleteCookies("HI_APP_SESSION"))
                .addFilterBefore(new ActiveUserFilter(profiles), AuthorizationFilter.class)
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
                                        + "img-src 'self' data: blob:; font-src 'self'; frame-ancestors 'self'"))
                        .referrerPolicy(referrer -> referrer.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)));
        return http.build();
    }

    /**
     * Only page navigations (GET, Accept text/html) are remembered to continue after the login.
     * Otherwise a request the browser makes on its own in the meantime, such as Safari's
     * {@code /apple-touch-icon.png}, replaces the saved one and the user lands on a 404 after logging in.
     */
    static RequestCache pageRequestCache() {
        MediaTypeRequestMatcher html = new MediaTypeRequestMatcher(MediaType.TEXT_HTML);
        html.setIgnoredMediaTypes(Set.of(MediaType.ALL));
        HttpSessionRequestCache cache = new HttpSessionRequestCache();
        cache.setRequestMatcher(new AndRequestMatcher(request -> "GET".equals(request.getMethod()), html));
        return cache;
    }
}
