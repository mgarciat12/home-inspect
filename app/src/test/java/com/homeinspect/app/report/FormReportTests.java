package com.homeinspect.app.report;

import com.homeinspect.app.TestUsers;
import com.homeinspect.app.TestcontainersConfiguration;
import com.homeinspect.app.catalog.CatalogService;
import com.homeinspect.app.catalog.FieldType;
import com.homeinspect.app.pdf.PdfService;
import com.homeinspect.app.photo.PhotoService;
import com.homeinspect.app.photo.TestImages;
import com.homeinspect.app.report.dto.ItemUpdate;
import com.homeinspect.app.report.dto.ReportEditorView.ItemView;
import com.homeinspect.app.report.dto.ReportEditorView;
import com.homeinspect.app.report.dto.ReportHeaderForm;
import com.homeinspect.app.security.CurrentUser;
import com.homeinspect.app.user.LicenseService;
import com.homeinspect.app.user.LicenseType;
import com.homeinspect.app.user.Role;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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

/** 4-Point (Citizens Insp4pt 03 25) and wind mitigation (OIR-B1-1802 Rev. 04/26) forms. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestUsers.class})
class FormReportTests {

    @Autowired
    MockMvc mvc;
    @Autowired
    TestUsers users;
    @Autowired
    ReportService reports;
    @Autowired
    PhotoService photos;
    @Autowired
    PdfService pdfs;
    @Autowired
    CatalogService catalog;
    @Autowired
    LicenseService licenses;

    long create(ReportType type, String address, CurrentUser user) {
        ReportHeaderForm form = new ReportHeaderForm();
        form.setType(type);
        form.setInspectedAt(LocalDateTime.of(2026, 6, 2, 9, 0));
        form.setClientName("Jane Owner");
        form.setPolicyNumber("PR-778899");
        form.setAddress(address);
        form.setCity("Tampa");
        form.setState("FL");
        form.setPostalCode("33602");
        form.setCounty("Hillsborough");
        form.setYearBuilt(1996);
        return reports.create(form, user);
    }

    ItemView question(long id, String section, String code, CurrentUser user) {
        ReportEditorView editor = reports.editor(id, section, user);
        return editor.current().items().stream().filter(i -> i.code().equals(code)).findFirst().orElseThrow();
    }

    void answer(long id, ItemView item, CurrentUser user, String comments, String... answers) {
        reports.updateItem(id, item.id(), ItemUpdate.answers(List.of(answers), comments), user);
    }

    @Test
    void everyTypeHasACatalog() {
        assertThat(catalog.availableTypes()).containsExactlyInAnyOrder(ReportType.values());
        assertThat(catalog.activeSections(ReportType.FOUR_POINT)).extracting(s -> s.getCode())
                .containsExactly("electrical", "hvac", "plumbing", "roof", "roof_secondary", "photos");
        assertThat(catalog.activeSections(ReportType.WIND_MITIGATION)).extracting(s -> s.getCode())
                .containsExactly("owner", "building_code", "region", "roof_slope", "roof_covering", "roof_deck",
                        "roof_to_wall", "roof_geometry", "swr", "openings", "certification");
    }

    @Test
    void theCommentLibraryCoversEveryItemOfEveryType() throws Exception {
        for (ReportType type : ReportType.values()) {
            var items = catalog.activeSections(type).stream().flatMap(s -> catalog.activeItems(s).stream()).toList();
            var comments = catalog.commentsByItem(items.stream().map(i -> i.getId()).toList());
            assertThat(items).as(type.name()).allSatisfy(i ->
                    assertThat(comments.getOrDefault(i.getId(), List.of())).as(i.getCode()).isNotEmpty());
        }
        mvc.perform(get("/admin/comments").param("type", "WIND_MITIGATION").param("section", "roof_deck")
                        .with(users.login("form.library", Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nail spacing verified in the attic")))
                .andExpect(content().string(containsString("4-Point")));
    }

    @Test
    void answersAreValidatedAgainstTheQuestionType() {
        CurrentUser user = users.current("form.answers", Role.INSPECTOR);
        long id = create(ReportType.FOUR_POINT, "1 Answer Way", user);
        assertThat(reports.number(id, user)).startsWith("4P-");

        ItemView type = question(id, "electrical", "main_panel_type", user);
        assertThat(type.question().type()).isEqualTo(FieldType.CHOICE);
        answer(id, type, user, null, "Not an option", "Fuse", "Circuit breaker");
        assertThat(question(id, "electrical", "main_panel_type", user).question().answers()).containsExactly("Fuse");

        ItemView hazards = question(id, "electrical", "hazards", user);
        answer(id, hazards, user, "Double tap on breaker 12", "Double taps", "Scorching", "Invented");
        assertThat(question(id, "electrical", "hazards", user).question().answers()).containsExactly("Double taps", "Scorching");

        ItemView amps = question(id, "electrical", "main_panel_amps", user);
        answer(id, amps, user, null, "two hundred");
        assertThat(question(id, "electrical", "main_panel_amps", user).question().answers()).isEmpty();
        answer(id, amps, user, null, "200");
        assertThat(question(id, "electrical", "main_panel_amps", user).completed()).isTrue();

        ItemView lastService = question(id, "hvac", "last_service", user);
        answer(id, lastService, user, null, "2025-13-40");
        assertThat(question(id, "hvac", "last_service", user).question().answers()).isEmpty();
        answer(id, lastService, user, null, "2025-08-21");
        assertThat(question(id, "hvac", "last_service", user).question().value()).isEqualTo("2025-08-21");
    }

    @Test
    void fourPointCannotBeFinalizedWithoutAnswersExplanationsAndPhotos() throws Exception {
        CurrentUser user = users.current("form.final", Role.INSPECTOR);
        long id = create(ReportType.FOUR_POINT, "2 Final Ave", user);
        answer(id, question(id, "plumbing", "active_leak", user), user, null, "Yes");

        assertThatThrownBy(() -> reports.finalizeReport(id, user))
                .isInstanceOfSatisfying(ReportIncompleteException.class, e -> assertThat(e.problems())
                        .contains(new ReportIncompleteException.Problem("1.1 Main panel: type", "report.problem.answer"))
                        .contains(new ReportIncompleteException.Problem(
                                "3.2 Is there any indication of an active leak?", "report.problem.explain"))
                        .contains(new ReportIncompleteException.Problem("6.1 Dwelling: front", "report.problem.photo")));

        // Uploading a photo answers the photo slot
        ItemView front = question(id, "photos", "dwelling_front", user);
        photos.upload(id, front.id(), List.of(new MockMultipartFile("files", "front.jpg", "image/jpeg",
                TestImages.jpeg(TestImages.halves(800, 600)))), user);
        assertThat(question(id, "photos", "dwelling_front", user).question().answers()).containsExactly(FieldType.ATTACHED);
    }

    @Test
    void fourPointPdfReproducesTheForm() throws Exception {
        CurrentUser user = users.current("form.pdf", Role.INSPECTOR);
        licenses.add(user.profileId(), LicenseType.ROOFING_CONTRACTOR, "CCC1300000");
        licenses.add(user.profileId(), LicenseType.HOME_INSPECTOR, "HI9001");
        long id = create(ReportType.FOUR_POINT, "3 Form St", user);
        answer(id, question(id, "electrical", "main_panel_type", user), user, null, "Circuit breaker");
        answer(id, question(id, "electrical", "main_panel_amps", user), user, null, "200");
        answer(id, question(id, "plumbing", "active_leak", user), user, "Slow drip at the kitchen trap.", "Yes");

        PdfService.Pdf pdf = pdfs.render(id, user);
        assertThat(pdf.fileName()).startsWith("4-point-2026-");
        try (PDDocument document = Loader.loadPDF(pdf.content())) {
            String text = new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
            assertThat(text)
                    .contains("4-Point Inspection Form")
                    .contains("Insured/Applicant Name:")
                    .contains("Jane Owner")
                    .contains("PR-778899")
                    .contains("☒ Circuit breaker")
                    .contains("☐ Fuse")
                    .contains("Main panel: total amps: 200")
                    .contains("Slow drip at the kitchen trap.")
                    .contains("Additional Comments/Observations")
                    .contains("I certify that the above statements are true and correct.")
                    // The license accepted for a 4-Point is printed, not the roofing one
                    .contains("HI9001")
                    .doesNotContain("CCC1300000");
        }
        mvc.perform(get("/reports/{id}/preview", id).with(users.login("form.pdf", Role.INSPECTOR)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("4-Point Inspection Form")))
                .andExpect(content().string(containsString("Back to the editor")));
    }

    @Test
    void windMitigationPdfReproducesTheForm() throws Exception {
        CurrentUser user = users.current("form.wm", Role.INSPECTOR);
        licenses.add(user.profileId(), LicenseType.BUILDING_CONTRACTOR, "CBC1250000");
        long id = create(ReportType.WIND_MITIGATION, "4 Hurricane Blvd", user);
        assertThat(reports.number(id, user)).startsWith("WM-");

        ItemView deck = question(id, "roof_deck", "answer", user);
        assertThat(deck.question().explainOn()).containsExactly("F. Other");
        assertThat(deck.question().photoRequired()).isTrue();
        assertThat(deck.question().compact()).isFalse();
        answer(id, deck, user, null, deck.question().options().get(2));
        answer(id, question(id, "region", "answer", user), user, null, "Region 2 (130 mph – 139 mph)");
        answer(id, question(id, "owner", "cell_phone", user), user, null, "(813) 555-0199");

        assertThatThrownBy(() -> reports.finalizeReport(id, user))
                .isInstanceOfSatisfying(ReportIncompleteException.class, e -> assertThat(e.problems())
                        .contains(new ReportIncompleteException.Problem("6.1 Weakest form of roof deck attachment",
                                "report.problem.photo")));

        try (PDDocument document = Loader.loadPDF(pdfs.render(id, user).content())) {
            String text = new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
            assertThat(text)
                    .contains("Uniform Mitigation Verification Inspection Form")
                    .contains("OIR-B1-1802 (Rev. 04/26)")
                    .contains("Hillsborough")
                    .contains("(813) 555-0199")
                    .contains("☒ Region 2 (130 mph – 139 mph)")
                    .contains("☒ C. Plywood/OSB roof sheathing")
                    .contains("MITIGATION INSPECTIONS MUST BE CERTIFIED BY A QUALIFIED INSPECTOR.")
                    .contains("CBC1250000")
                    .contains("Homeowner to complete")
                    .contains("valid for up to five (5) years");
        }
    }
}
