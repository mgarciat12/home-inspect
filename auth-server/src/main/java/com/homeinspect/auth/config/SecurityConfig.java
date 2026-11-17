package com.homeinspect.auth.config;

import com.homeinspect.auth.web.LoginFailureHandler;
import com.homeinspect.auth.web.LoginSuccessHandler;
import com.homeinspect.auth.web.LoginStepFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

import java.util.Set;

/**
 * Three filter chains:
 * <ol>
 *     <li>OAuth2 / OpenID Connect endpoints (authorize, token, JWKS, userinfo, logout...).</li>
 *     <li>{@code /api/**}: user administration API, bearer JWT with scope {@code users.admin}.</li>
 *     <li>Web pages: own login form, mandatory password change.</li>
 * </ol>
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
class SecurityConfig {

    static final String USERS_ADMIN_AUTHORITY = "SCOPE_users.admin";

    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerFilterChain(HttpSecurity http) {
        http.oauth2AuthorizationServer(authorizationServer -> {
                    http.securityMatcher(authorizationServer.getEndpointsMatcher());
                    authorizationServer.oidc(Customizer.withDefaults());
                })
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                // Browsers that are not logged in go to the login page
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
                // userinfo endpoint accepts access tokens
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain apiFilterChain(HttpSecurity http) {
        http.securityMatcher("/api/**")
                .authorizeHttpRequests(authorize -> authorize.anyRequest().hasAuthority(USERS_ADMIN_AUTHORITY))
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain webFilterChain(HttpSecurity http, LoginSuccessHandler successHandler,
                                       LoginFailureHandler failureHandler) {
        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/login", "/css/**", "/js/**", "/webjars/**", "/favicon.ico", "/apple-touch-icon*.png", "/error").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .requestCache(cache -> cache.requestCache(pageRequestCache()))
                .formLogin(form -> form.loginPage("/login")
                        .successHandler(successHandler)
                        .failureHandler(failureHandler)
                        .permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
                                        + "img-src 'self' data:; font-src 'self'; frame-ancestors 'none'")));
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

    @Bean
    PasswordEncoder passwordEncoder() {
        // BCrypt by default; the {id} prefix allows changing the algorithm later
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * Runs before Spring Security so that a user with a pending login step (two-step code,
     * temporary password...) cannot reach the authorization endpoint nor any other page.
     */
    @Bean
    FilterRegistrationBean<LoginStepFilter> loginStepFilter() {
        FilterRegistrationBean<LoginStepFilter> registration = new FilterRegistrationBean<>(new LoginStepFilter());
        registration.setOrder(-200);
        return registration;
    }
}
