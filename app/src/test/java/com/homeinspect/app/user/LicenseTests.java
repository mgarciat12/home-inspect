package com.homeinspect.app.user;

import com.homeinspect.app.TestUsers;
import com.homeinspect.app.TestcontainersConfiguration;
import com.homeinspect.app.document.ReportDocumentService;
import com.homeinspect.app.report.ReportService;
import com.homeinspect.app.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static com.homeinspect.app.report.ReportFixtures.header;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/** Florida licenses of each user: "My profile" and the signature block of the reports. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestUsers.class})
class LicenseTests {

    @Autowired
    MockMvc mvc;
    @Autowired
    TestUsers users;
    @Autowired
    LicenseService licenses;
    @Autowired
    ReportService reports;
    @Autowired
    ReportDocumentService documents;

    @Test
    void userManagesTheirLicensesInTheProfile() throws Exception {
        var login = users.login("lic.web", Role.INSPECTOR);
        mvc.perform(post("/profile/licenses").with(login).with(csrf())
                        .param("type", "HOME_INSPECTOR").param("number", " hi1234 "))
                .andExpect(redirectedUrl("/profile#licenses"))
                .andExpect(flash().attribute("message", "License added."));
        mvc.perform(get("/profile").with(login))
                .andExpect(content().string(containsString("Home inspector (468.8314)")))
                .andExpect(content().string(containsString("HI1234")));

        mvc.perform(post("/profile/licenses").with(login).with(csrf())
                        .param("type", "HOME_INSPECTOR").param("number", "HI1234"))
                .andExpect(flash().attribute("error", "You already have that license."));
        mvc.perform(post("/profile/licenses").with(login).with(csrf())
                        .param("type", "ROOFING_CONTRACTOR").param("number", "<script>"))
                .andExpect(flash().attribute("error", containsString("Invalid license number")));

        long id = licenses.list(users.current("lic.web", Role.INSPECTOR).profileId()).getFirst().id();
        mvc.perform(post("/profile/licenses/{id}/delete", id).with(login).with(csrf()))
                .andExpect(redirectedUrl("/profile#licenses"));
        assertThat(licenses.list(users.current("lic.web", Role.INSPECTOR).profileId())).isEmpty();
    }

    @Test
    void aUserCannotDeleteSomeoneElsesLicense() throws Exception {
        CurrentUser owner = users.current("lic.owner", Role.INSPECTOR);
        licenses.add(owner.profileId(), LicenseType.PROFESSIONAL_ENGINEER, "PE 98765");
        long id = licenses.list(owner.profileId()).getFirst().id();
        mvc.perform(post("/profile/licenses/{id}/delete", id).with(users.login("lic.intruder", Role.INSPECTOR)).with(csrf()));
        assertThat(licenses.list(owner.profileId())).hasSize(1);
    }

    @Test
    void numberOfLicensesIsLimited() {
        CurrentUser user = users.current("lic.many", Role.INSPECTOR);
        for (int i = 0; i < LicenseService.MAX_LICENSES; i++) {
            licenses.add(user.profileId(), LicenseType.OTHER, "N" + i);
        }
        assertThatThrownBy(() -> licenses.add(user.profileId(), LicenseType.OTHER, "N99"))
                .isInstanceOf(LicenseService.InvalidLicenseException.class)
                .hasMessage("profile.license.tooMany");
    }

    @Test
    void reportsShowTheInspectorLicenses() {
        CurrentUser user = users.current("lic.report", Role.INSPECTOR);
        licenses.add(user.profileId(), LicenseType.HOME_INSPECTOR, "HI5555");
        licenses.add(user.profileId(), LicenseType.GENERAL_CONTRACTOR, "CGC1500000");
        long id = reports.create(header("Calle Licencia 1"), user);
        assertThat(documents.build(id, user).inspector().licenses())
                .extracting(LicenseService.License::number).containsExactly("HI5555", "CGC1500000");
    }
}
