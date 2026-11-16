package com.homeinspect.app.summary;

import com.homeinspect.app.TestUsers;
import com.homeinspect.app.TestcontainersConfiguration;
import com.homeinspect.app.document.ReportDocument;
import com.homeinspect.app.document.ReportDocument.Finding;
import com.homeinspect.app.document.ReportDocument.FindingGroup;
import com.homeinspect.app.document.ReportDocumentService;
import com.homeinspect.app.photo.PhotoService;
import com.homeinspect.app.photo.TestImages;
import com.homeinspect.app.report.ItemStatus;
import com.homeinspect.app.report.Rating;
import com.homeinspect.app.report.Recommendation;
import com.homeinspect.app.report.ReportNotFoundException;
import com.homeinspect.app.report.ReportService;
import com.homeinspect.app.report.dto.ItemUpdate;
import com.homeinspect.app.report.dto.ReportEditorView;
import com.homeinspect.app.report.dto.ReportHeaderForm;
import com.homeinspect.app.report.dto.SectionUpdate;
import com.homeinspect.app.security.CurrentUser;
import com.homeinspect.app.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestUsers.class})
class FindingsSummaryTests {

    @Autowired
    ReportService reports;
    @Autowired
    ReportDocumentService documents;
    @Autowired
    PhotoService photos;
    @Autowired
    TestUsers users;
    @Autowired
    MockMvc mvc;

    long reportWithFindings(CurrentUser user) {
        ReportHeaderForm form = new ReportHeaderForm();
        form.setInspectedAt(LocalDateTime.of(2026, 7, 1, 10, 0));
        form.setAddress("Calle Hallazgos 1");
        form.setCity("Valencia");
        long id = reports.create(form, user);

        ReportEditorView roof = reports.editor(id, "roof", user);
        long covering = roof.current().items().get(0).id();
        long gutters = roof.current().items().get(1).id();
        long flashings = roof.current().items().get(2).id();
        rate(id, covering, Rating.MAINTENANCE, "Limpiar musgo de la cubierta", user);
        rate(id, gutters, Rating.SAFETY_HAZARD, "Canalón suelto sobre la entrada principal", user);
        rate(id, flashings, Rating.SATISFACTORY, "Correcto", user);
        photos.upload(id, gutters, List.of(new MockMultipartFile("files", "canalon.jpg", "image/jpeg",
                TestImages.jpeg(TestImages.halves(800, 600)))), user);

        ReportEditorView electrical = reports.editor(id, "electrical", user);
        rate(id, electrical.current().items().get(2).id(), Rating.REPAIR, "Falta diferencial", user);
        rate(id, electrical.current().items().get(5).id(), Rating.SAFETY_HAZARD, "Sin detectores de humo", user);

        // Findings of a section marked as not applicable are not listed
        ReportEditorView garage = reports.editor(id, "garage", user);
        rate(id, garage.current().items().getFirst().id(), Rating.SPECIALIST, "Puerta", user);
        reports.updateSection(id, garage.current().id(), new SectionUpdate(true, null, null, null), user);
        return id;
    }

    void rate(long reportId, long itemId, Rating rating, String observations, CurrentUser user) {
        reports.updateItem(reportId, itemId, new ItemUpdate(ItemStatus.INSPECTED, rating, List.of(), null, List.of(),
                null, observations, rating == Rating.SATISFACTORY ? null : Recommendation.REPAIR, null), user);
    }

    @Test
    void findingsAreGroupedBySeverityWithReferenceExcerptAndPhoto() {
        CurrentUser user = users.current("fs.inspector", Role.INSPECTOR);
        ReportDocument doc = documents.build(reportWithFindings(user), user);

        assertThat(doc.findings()).extracting(FindingGroup::rating)
                .containsExactly(Rating.SAFETY_HAZARD, Rating.REPAIR, Rating.MAINTENANCE);
        assertThat(doc.findingCount()).isEqualTo(4);

        List<Finding> hazards = doc.findings().getFirst().findings();
        assertThat(hazards).extracting(Finding::label)
                .containsExactly("1.2 Tejado › Canalones y bajantes", "7.6 Electricidad › Detectores de humo y CO");
        Finding gutter = hazards.getFirst();
        assertThat(gutter.excerpt()).isEqualTo("Canalón suelto sobre la entrada principal");
        assertThat(gutter.recommendation()).isEqualTo(Recommendation.REPAIR);
        assertThat(gutter.photo()).isNotNull();
        assertThat(gutter.photo().thumbnailUrl()).endsWith("/thumb");
        assertThat(hazards.get(1).photo()).isNull();

        assertThat(doc.findings()).flatExtracting(FindingGroup::findings)
                .noneMatch(f -> f.sectionCode().equals("garage"))
                .noneMatch(f -> f.itemTitle().equals("Tapajuntas y remates"));
    }

    @Test
    void documentContainsTheWholeReportInOrder() {
        CurrentUser user = users.current("fs.doc", Role.INSPECTOR);
        ReportDocument doc = documents.build(reportWithFindings(user), user);
        assertThat(doc.sections()).hasSize(11);
        assertThat(doc.sections().getLast().notApplicable()).isTrue();
        assertThat(doc.property().fullAddress()).isEqualTo("Calle Hallazgos 1, Valencia");
        assertThat(doc.company().scopeText()).contains("inspección es un examen visual");
        assertThat(doc.sections().getFirst().items().get(1).photos()).hasSize(1);
    }

    @Test
    void longObservationsAreCutAtAWord() {
        String text = "Palabra ".repeat(60);
        String excerpt = FindingsSummaryService.excerpt(text);
        assertThat(excerpt).endsWith("Palabra…").hasSizeLessThanOrEqualTo(FindingsSummaryService.EXCERPT_LENGTH + 1);
        assertThat(FindingsSummaryService.excerpt("  corta \n  ")).isEqualTo("corta");
        assertThat(FindingsSummaryService.excerpt(null)).isNull();
    }

    @Test
    void onlyAllowedUsersCanBuildTheDocument() {
        CurrentUser owner = users.current("fs.owner", Role.INSPECTOR);
        long id = reportWithFindings(owner);
        assertThatThrownBy(() -> documents.build(id, users.current("fs.other", Role.INSPECTOR)))
                .isInstanceOf(ReportNotFoundException.class);
        assertThat(documents.build(id, users.current("fs.admin", Role.ADMIN)).number()).isNotBlank();
    }

    @Test
    void previewPageShowsTheSummaryAndTheSections() throws Exception {
        CurrentUser user = users.current("fs.preview", Role.INSPECTOR);
        long id = reportWithFindings(user);
        mvc.perform(get("/reports/{id}/preview", id).with(users.login("fs.preview", Role.INSPECTOR)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Informe de inspección de vivienda")))
                .andExpect(content().string(containsString("Resumen de hallazgos")))
                .andExpect(content().string(containsString("1.2 Tejado › Canalones y bajantes")))
                .andExpect(content().string(containsString("Leyenda de valoraciones")))
                .andExpect(content().string(containsString("Esta sección no aplica")))
                .andExpect(content().string(containsString("Falta diferencial")));
        mvc.perform(get("/reports/{id}/preview", id).with(users.login("fs.preview.other", Role.INSPECTOR)))
                .andExpect(status().isNotFound());
    }
}
