package com.homeinspect.app;

import com.homeinspect.app.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestUsers.class})
class HomeInspectApplicationTests {

    @Autowired
    MockMvc mvc;
    @Autowired
    TestUsers users;

    @Test
    void anonymousUsersAreSentToTheAuthServer() throws Exception {
        mvc.perform(get("/")).andExpect(redirectedUrl("/oauth2/authorization/home-inspect"));
    }

    @Test
    void authorizationRequestUsesPkce() throws Exception {
        mvc.perform(get("/oauth2/authorization/home-inspect"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("http://auth.test:9000/oauth2/authorize")))
                .andExpect(header().string("Location", containsString("code_challenge_method=S256")));
    }

    @Test
    void homeIsTheReportList() throws Exception {
        mvc.perform(get("/").with(users.login("home.user", Role.INSPECTOR)))
                .andExpect(redirectedUrl("/reports"));
    }

    @Test
    void reportListRendersWithLayout() throws Exception {
        mvc.perform(get("/reports").with(users.login("home.user", Role.INSPECTOR)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("New report")))
                .andExpect(content().string(containsString("Name home.user")))
                .andExpect(content().string(containsString("/webjars/bootstrap/5.3.9/dist/css/bootstrap.min.css")));
    }

    @Test
    void bootstrapIsServedFromWebJarWithoutLogin() throws Exception {
        mvc.perform(get("/webjars/bootstrap/dist/css/bootstrap.min.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bootstrap  v5.3.9")));
    }

    @Test
    void securityHeaders() throws Exception {
        mvc.perform(get("/reports").with(users.login("home.headers", Role.INSPECTOR)))
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self';")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().exists("X-Frame-Options"))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    void onlyTheHealthEndpointOfActuatorIsExposed() throws Exception {
        mvc.perform(get("/actuator/env").with(users.login("home.actuator", Role.ADMIN))).andExpect(status().isNotFound());
        mvc.perform(get("/actuator/health")).andExpect(content().string(org.hamcrest.Matchers.not(containsString("components"))));
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void loginErrorPageIsPublic() throws Exception {
        mvc.perform(get("/login-error"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Could not sign in")));
    }
}
