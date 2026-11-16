package com.homeinspect.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

/**
 * {@link RestClient} for the user administration API of the auth-server, authenticated with a
 * client-credentials token of the {@code home-inspect-admin-api} client (scope users.admin).
 */
@Configuration(proxyBeanMethods = false)
class AuthServerClientConfig {

    static final String ADMIN_API_REGISTRATION_ID = "home-inspect-admin-api";

    /** Client-credentials manager: works outside a browser request and caches the token until it expires. */
    @Bean
    OAuth2AuthorizedClientManager adminApiAuthorizedClientManager(ClientRegistrationRepository clients,
                                                                  OAuth2AuthorizedClientService authorizedClients) {
        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(clients, authorizedClients);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());
        return manager;
    }

    @Bean
    RestClient authServerRestClient(RestClient.Builder builder, OAuth2AuthorizedClientManager adminApiAuthorizedClientManager,
                                    @Value("${home-inspect.auth-server.url}") String authServerUrl) {
        OAuth2ClientHttpRequestInterceptor interceptor = new OAuth2ClientHttpRequestInterceptor(adminApiAuthorizedClientManager);
        interceptor.setClientRegistrationIdResolver(request -> ADMIN_API_REGISTRATION_ID);
        // Token belongs to the app, not to the logged-in user
        interceptor.setPrincipalResolver(request -> null);
        return builder.baseUrl(authServerUrl).requestInterceptor(interceptor).build();
    }
}
