package com.homeinspect.app.settings;

import com.homeinspect.app.TestUsers;
import com.homeinspect.app.TestcontainersConfiguration;
import com.homeinspect.app.catalog.CommentLibraryService;
import com.homeinspect.app.photo.TestImages;
import com.homeinspect.app.report.ReportService;
import com.homeinspect.app.report.dto.ReportHeaderForm;
import com.homeinspect.app.security.CurrentUser;
import com.homeinspect.app.user.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.awt.image.BufferedImage;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestUsers.class})
class AdminSettingsTests {

    @Autowired
    MockMvc mvc;
    @Autowired
    TestUsers users;
    @Autowired
    CompanySettingsService settings;
    @Autowired
    CommentLibraryService library;
    @Autowired
    ReportService reports;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminEditsCompanyDataAndTexts() throws Exception {
        var admin = users.login("set.admin", Role.ADMIN);
        mvc.perform(get("/admin/settings").with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Alcance y limitaciones")));
        mvc.perform(post("/admin/settings").with(admin).with(csrf())
                        .param("companyName", "Inspecciones Ejemplo S.L.")
                        .param("phone", "910 000 000")
                        .param("email", "info@example.com")
                        .param("scopeText", "Alcance personalizado.")
                        .param("legalText", "Texto legal de prueba."))
                .andExpect(redirectedUrl("/admin/settings"));
        CompanyView view = settings.view();
        assertThat(view.name()).isEqualTo("Inspecciones Ejemplo S.L.");
        assertThat(view.scopeText()).isEqualTo("Alcance personalizado.");
        assertThat(view.address()).isNull();

        mvc.perform(post("/admin/settings").with(admin).with(csrf()).param("email", "no-es-un-email"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Revisa los campos marcados")));
    }

    @Test
    void logoUploadServeAndDelete() throws Exception {
        var admin = users.login("set.logo", Role.ADMIN);
        MockMultipartFile logo = new MockMultipartFile("file", "logo.png", "image/png",
                TestImages.png(new BufferedImage(1200, 400, BufferedImage.TYPE_INT_ARGB)));
        mvc.perform(multipart("/admin/settings/logo").file(logo).with(admin).with(csrf()))
                .andExpect(redirectedUrl("/admin/settings"));
        mvc.perform(get("/settings/logo").with(users.login("set.viewer", Role.INSPECTOR)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));
        assertThat(settings.view().hasLogo()).isTrue();

        mvc.perform(post("/admin/settings/logo/delete").with(admin).with(csrf())).andExpect(redirectedUrl("/admin/settings"));
        mvc.perform(get("/settings/logo").with(admin)).andExpect(status().isNotFound());
    }

    @Test
    void inspectorsCannotChangeSettingsOrComments() throws Exception {
        var inspector = users.login("set.inspector", Role.INSPECTOR);
        mvc.perform(get("/admin/settings").with(inspector)).andExpect(status().isForbidden());
        mvc.perform(post("/admin/settings").with(inspector).with(csrf()).param("companyName", "X"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/comments").with(inspector)).andExpect(status().isForbidden());

        // Also enforced in the service layer
        users.authenticate("set.inspector2", Role.INSPECTOR);
        assertThatThrownBy(() -> settings.update(new CompanySettingsForm())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> library.sections()).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void commentLibraryIsEditableAndUsedByTheEditor() throws Exception {
        var admin = users.login("set.comments", Role.ADMIN);
        users.authenticate("set.comments", Role.ADMIN);
        var item = library.items("plumbing").getFirst();
        int before = item.comments().size();
        SecurityContextHolder.clearContext();

        mvc.perform(get("/admin/comments").param("section", "plumbing").with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Llave de paso general")));
        mvc.perform(post("/admin/comments").with(admin).with(csrf())
                        .param("itemId", String.valueOf(item.itemId()))
                        .param("text", "Llave de paso general agarrotada; se recomienda sustituirla."))
                .andExpect(redirectedUrlPattern("/admin/comments?section=plumbing*"));

        users.authenticate("set.comments", Role.ADMIN);
        var comments = library.items("plumbing").getFirst().comments();
        assertThat(comments).hasSize(before + 1);
        long added = comments.getLast().id();
        library.move(added, true);
        assertThat(library.items("plumbing").getFirst().comments().get(before - 1).id()).isEqualTo(added);
        library.update(added, "Llave de paso agarrotada.");
        SecurityContextHolder.clearContext();

        // The inspectors see it in the editor
        CurrentUser inspector = users.current("set.editor", Role.INSPECTOR);
        ReportHeaderForm form = new ReportHeaderForm();
        form.setInspectedAt(LocalDateTime.of(2026, 10, 1, 9, 0));
        form.setAddress("Calle Comentarios 1");
        long reportId = reports.create(form, inspector);
        mvc.perform(get("/reports/{id}/edit", reportId).param("section", "plumbing").with(users.login("set.editor", Role.INSPECTOR)))
                .andExpect(content().string(containsString("Llave de paso agarrotada.")));

        mvc.perform(post("/admin/comments/{id}/delete", added).with(admin).with(csrf())
                        .param("itemId", String.valueOf(item.itemId())))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/reports/{id}/edit", reportId).param("section", "plumbing").with(users.login("set.editor", Role.INSPECTOR)))
                .andExpect(content().string(not(containsString("Llave de paso agarrotada."))));
    }
}
