package com.homeinspect.app.i18n;

import com.homeinspect.app.TestUsers;
import com.homeinspect.app.TestcontainersConfiguration;
import com.homeinspect.app.report.ReportService;
import com.homeinspect.app.security.CurrentUser;
import com.homeinspect.app.user.Role;
import com.homeinspect.app.user.UserProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static com.homeinspect.app.report.ReportFixtures.header;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/** Bilingual interface: browser language, the user's choice, and reports always in English. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestUsers.class})
class LanguageTests {

    @Autowired
    MockMvc mvc;
    @Autowired
    TestUsers users;
    @Autowired
    UserProfileService profiles;
    @Autowired
    ReportService reports;

    @Test
    void followsTheBrowserLanguageWithEnglishByDefault() throws Exception {
        var login = users.login("lang.browser", Role.INSPECTOR);
        mvc.perform(get("/reports").with(login))
                .andExpect(content().string(containsString("New report")))
                .andExpect(content().string(containsString("lang=\"en\"")));
        mvc.perform(get("/reports").with(login).header(HttpHeaders.ACCEPT_LANGUAGE, "es-ES,es;q=0.9"))
                .andExpect(content().string(containsString("Nuevo informe")))
                .andExpect(content().string(containsString("lang=\"es\"")));
        // Unsupported language: English
        mvc.perform(get("/reports").with(login).header(HttpHeaders.ACCEPT_LANGUAGE, "fr-FR"))
                .andExpect(content().string(containsString("New report")));
    }

    @Test
    void theLanguageChosenInTheProfileWinsOverTheBrowser() throws Exception {
        var login = users.login("lang.choice", Role.INSPECTOR);
        mvc.perform(post("/profile/language").with(login).with(csrf()).param("language", "es"))
                .andExpect(redirectedUrl("/profile"));
        assertThat(profiles.get(users.current("lang.choice", Role.INSPECTOR).profileId()).getLanguage()).isEqualTo("es");
        mvc.perform(get("/reports").with(login).header(HttpHeaders.ACCEPT_LANGUAGE, "en-US"))
                .andExpect(content().string(containsString("Nuevo informe")));

        // Unknown codes are stored as "follow the browser"
        mvc.perform(post("/profile/language").with(login).with(csrf()).param("language", "xx"));
        assertThat(profiles.get(users.current("lang.choice", Role.INSPECTOR).profileId()).getLanguage()).isNull();
        mvc.perform(get("/reports").with(login).header(HttpHeaders.ACCEPT_LANGUAGE, "en-US"))
                .andExpect(content().string(containsString("New report")));
    }

    @Test
    void thePreviewIsAlwaysInEnglish() throws Exception {
        var login = users.login("lang.preview", Role.INSPECTOR);
        CurrentUser user = users.current("lang.preview", Role.INSPECTOR);
        profiles.setLanguage(user.profileId(), "es");
        long id = reports.create(header("Calle Vista 1"), user);
        mvc.perform(get("/reports/{id}/preview", id).with(login).header(HttpHeaders.ACCEPT_LANGUAGE, "es"))
                .andExpect(content().string(containsString("Home Inspection Report")))
                .andExpect(content().string(containsString("Summary of findings")));
        // The editor keeps the user's language
        mvc.perform(get("/reports/{id}/edit", id).with(login))
                .andExpect(content().string(containsString("Vista previa")));
    }
}
