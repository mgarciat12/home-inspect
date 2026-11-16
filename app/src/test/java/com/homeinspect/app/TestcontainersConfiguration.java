package com.homeinspect.app;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL in a container, and static OAuth2 client registrations so that the tests do not need
 * a running auth-server (no OIDC discovery).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    static final String ISSUER = "http://auth.test:9000";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
    }

    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
        ClientRegistration web = ClientRegistration.withRegistrationId("home-inspect")
                .clientId("home-inspect-web")
                .clientSecret("web-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile")
                .issuerUri(ISSUER)
                .authorizationUri(ISSUER + "/oauth2/authorize")
                .tokenUri(ISSUER + "/oauth2/token")
                .jwkSetUri(ISSUER + "/oauth2/jwks")
                .userInfoUri(ISSUER + "/userinfo")
                .userNameAttributeName("sub")
                .build();
        ClientRegistration adminApi = ClientRegistration.withRegistrationId("home-inspect-admin-api")
                .clientId("home-inspect-admin-api")
                .clientSecret("admin-api-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("users.admin")
                .tokenUri(ISSUER + "/oauth2/token")
                .build();
        return new InMemoryClientRegistrationRepository(web, adminApi);
    }
}
