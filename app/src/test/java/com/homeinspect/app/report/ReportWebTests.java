package com.homeinspect.app.report;

import com.homeinspect.app.TestUsers;
import com.homeinspect.app.TestcontainersConfiguration;
import com.homeinspect.app.security.CurrentUser;
import com.homeinspect.app.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static com.homeinspect.app.report.ReportFixtures.header;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestUsers.class})
class ReportWebTests {

    @Autowired
    MockMvc mvc;
    @Autowired
    TestUsers users;
    @Autowired
    ReportService reports;

    @Test
    void createReportFromTheForm() throws Exception {
        mvc.perform(post("/reports").with(users.login("rw.create", Role.INSPECTOR)).with(csrf())
                        .param("inspectedAt", "2026-05-02T09:15")
                        .param("address", "Calle Web 1")
                        .param("propertyType", "DUPLEX")
                        .param("areaM2", "120"))
                .andExpect(redirectedUrlPattern("/reports/*/edit"));
    }

    @Test
    void invalidHeaderShowsTheErrors() throws Exception {
        mvc.perform(post("/reports").with(users.login("rw.invalid", Role.INSPECTOR)).with(csrf())
                        .param("inspectedAt", "2026-05-02T09:15")
                        .param("address", "")
                        .param("yearBuilt", "3000"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Revisa los campos marcados")));
    }

    @Test
    void editorRendersTheSectionAndItems() throws Exception {
        CurrentUser inspector = users.current("rw.editor", Role.INSPECTOR);
        long id = reports.create(header("Calle Editor 2"), inspector);
        mvc.perform(get("/reports/{id}/edit", id).param("section", "electrical").with(users.login("rw.editor", Role.INSPECTOR)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Electricidad")))
                .andExpect(content().string(containsString("Detectores de humo y CO")))
                .andExpect(content().string(containsString("Peligro para la seguridad")))
                .andExpect(content().string(containsString("data-autosave=\"/reports/" + id + "/items/")));
    }

    @Test
    void autosaveEndpointSavesAnItem() throws Exception {
        CurrentUser inspector = users.current("rw.autosave", Role.INSPECTOR);
        long id = reports.create(header("Calle Autosave 3"), inspector);
        long itemId = reports.editor(id, null, inspector).current().items().getFirst().id();
        mvc.perform(put("/reports/{id}/items/{item}", id, itemId).with(users.login("rw.autosave", Role.INSPECTOR)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"INSPECTED","rating":"SAFETY_HAZARD","materials":["Pizarra"],
                                 "limitations":[],"observations":"Teja suelta sobre la entrada"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCompleted").value(true))
                .andExpect(jsonPath("$.sectionCompleted").value(1));
    }

    @Test
    void autosaveRequiresCsrf() throws Exception {
        CurrentUser inspector = users.current("rw.csrf", Role.INSPECTOR);
        long id = reports.create(header("Calle CSRF 4"), inspector);
        long itemId = reports.editor(id, null, inspector).current().items().getFirst().id();
        mvc.perform(put("/reports/{id}/items/{item}", id, itemId).with(users.login("rw.csrf", Role.INSPECTOR))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anotherInspectorGetsNotFound() throws Exception {
        CurrentUser owner = users.current("rw.owner", Role.INSPECTOR);
        long id = reports.create(header("Calle Ajena 5"), owner);
        long itemId = reports.editor(id, null, owner).current().items().getFirst().id();
        var intruder = users.login("rw.intruder", Role.INSPECTOR);
        mvc.perform(get("/reports/{id}/edit", id).with(intruder)).andExpect(status().isNotFound());
        mvc.perform(put("/reports/{id}/items/{item}", id, itemId).with(intruder).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INSPECTED\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void finalReportRejectsAutosaveWithConflict() throws Exception {
        CurrentUser inspector = users.current("rw.locked", Role.INSPECTOR);
        long id = reports.create(header("Calle Cerrada 6"), inspector);
        long itemId = reports.editor(id, null, inspector).current().items().getFirst().id();
        reports.finalizeReport(id, inspector);
        mvc.perform(put("/reports/{id}/items/{item}", id, itemId).with(users.login("rw.locked", Role.INSPECTOR)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INSPECTED\"}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/reports/{id}/edit", id).with(users.login("rw.locked", Role.INSPECTOR)))
                .andExpect(content().string(containsString("Informe finalizado")));
    }

    @Test
    void listShowsOwnReports() throws Exception {
        CurrentUser inspector = users.current("rw.list", Role.INSPECTOR);
        reports.create(header("Calle Listado 7"), inspector);
        mvc.perform(get("/reports").param("q", "listado").with(users.login("rw.list", Role.INSPECTOR)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Calle Listado 7")));
    }

    @Test
    void duplicateRedirectsToTheHeaderOfTheCopy() throws Exception {
        CurrentUser inspector = users.current("rw.dup", Role.INSPECTOR);
        long id = reports.create(header("Calle Plantilla 8"), inspector);
        mvc.perform(post("/reports/{id}/duplicate", id).with(users.login("rw.dup", Role.INSPECTOR)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Location", startsWith("/reports/")));
    }
}
