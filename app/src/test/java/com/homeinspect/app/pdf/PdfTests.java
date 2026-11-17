package com.homeinspect.app.pdf;

import com.homeinspect.app.TestUsers;
import com.homeinspect.app.TestcontainersConfiguration;
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
import com.homeinspect.app.security.CurrentUser;
import com.homeinspect.app.user.Role;
import com.homeinspect.app.user.SignatureService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestUsers.class})
class PdfTests {

    @Autowired
    PdfService pdfs;
    @Autowired
    ReportService reports;
    @Autowired
    PhotoService photos;
    @Autowired
    SignatureService signatures;
    @Autowired
    TestUsers users;
    @Autowired
    MockMvc mvc;

    long fullReport(CurrentUser user) {
        ReportHeaderForm form = new ReportHeaderForm();
        form.setInspectedAt(LocalDateTime.of(2026, 9, 10, 11, 30));
        form.setAddress("Calle Ñandú & Peñón 12, 3º");
        form.setCity("Logroño");
        form.setClientName("María José Núñez");
        long id = reports.create(form, user);

        ReportEditorView roof = reports.editor(id, "roof", user);
        long gutters = roof.current().items().get(1).id();
        reports.updateItem(id, gutters, new ItemUpdate(ItemStatus.INSPECTED, Rating.SAFETY_HAZARD,
                List.of("Zinc"), "Bajante de fundición", List.of("Altura o pendiente"), null,
                "Canalón suelto sobre la entrada.\nRiesgo de caída <peligro> & golpes.", Recommendation.REPAIR,
                "Fijar antes de una semana"), user);
        List<MockMultipartFile> files = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            files.add(new MockMultipartFile("files", "f" + i + ".jpg", "image/jpeg",
                    TestImages.jpeg(TestImages.halves(i == 1 ? 900 : 1600, i == 1 ? 1600 : 1200))));
        }
        var uploaded = photos.upload(id, gutters, List.copyOf(files), user).photos();
        photos.updateCaption(id, uploaded.getFirst().id(), "Canalón suelto", user);
        photos.toggleCover(id, uploaded.getFirst().id(), user);
        reports.updateItem(id, roof.current().items().getFirst().id(), new ItemUpdate(ItemStatus.NOT_PRESENT, null,
                List.of(), null, List.of(), null, null, null, null), user);
        signatures.save(user.profileId(), TestImages.png(new BufferedImage(600, 200, BufferedImage.TYPE_INT_ARGB)));
        return id;
    }

    @Test
    void generatesACompletePdf() throws Exception {
        CurrentUser user = users.current("pdf.full", Role.INSPECTOR);
        long id = fullReport(user);

        PdfService.Pdf pdf = pdfs.render(id, user);
        assertThat(new String(pdf.content(), 0, 5)).isEqualTo("%PDF-");
        assertThat(pdf.fileName()).isEqualTo("home-inspection-" + reports.number(id, user) + "-calle-nandu-penon-12-3.pdf");

        try (PDDocument document = Loader.loadPDF(pdf.content())) {
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(5);
            String text = new PDFTextStripper().getText(document);
            assertThat(text)
                    .contains("Home Inspection Report")
                    .contains(reports.number(id, user))
                    .contains("Calle Ñandú & Peñón 12, 3º")
                    .contains("María José Núñez")
                    .contains("Scope and limitations")
                    .contains("Summary of findings")
                    .contains("1.2 Roof Covering › Flashings")
                    .contains("Riesgo de caída <peligro> & golpes.")
                    .contains("Zinc, Bajante de fundición")
                    .contains("Canalón suelto")
                    .contains("Not present")
                    .contains("8. Interior Components")
                    .containsPattern("Page\\s+1\\s+of\\s+" + document.getNumberOfPages());
            // Images: cover + 3 photos + thumbnail + signature
            int images = 0;
            for (var page : document.getPages()) {
                for (var name : page.getResources().getXObjectNames()) {
                    if (page.getResources().isImageXObject(name)) {
                        images++;
                    }
                }
            }
            assertThat(images).isGreaterThanOrEqualTo(6);
            // DejaVu embedded (full coverage of accents and symbols)
            List<String> fonts = new ArrayList<>();
            for (var page : document.getPages()) {
                for (var name : page.getResources().getFontNames()) {
                    PDFont font = page.getResources().getFont(name);
                    fonts.add(font.getName());
                }
            }
            assertThat(fonts).anyMatch(name -> name.contains("DejaVu"));
        }
        // Optimized images keep the file reasonably small
        assertThat(pdf.content().length).isLessThan(3_000_000);
    }

    @Test
    void emptyDraftAlsoRenders() throws Exception {
        CurrentUser user = users.current("pdf.empty", Role.INSPECTOR);
        ReportHeaderForm form = new ReportHeaderForm();
        form.setInspectedAt(LocalDateTime.of(2026, 9, 11, 9, 0));
        form.setAddress("Vacía 1");
        long id = reports.create(form, user);
        try (PDDocument document = Loader.loadPDF(pdfs.render(id, user).content())) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("No findings were recorded").contains("Draft");
        }
    }

    @Test
    void onlyAllowedUsersGetThePdf() {
        CurrentUser owner = users.current("pdf.owner", Role.INSPECTOR);
        ReportHeaderForm form = new ReportHeaderForm();
        form.setInspectedAt(LocalDateTime.of(2026, 9, 12, 9, 0));
        form.setAddress("Privada 2");
        long id = reports.create(form, owner);
        assertThatThrownBy(() -> pdfs.render(id, users.current("pdf.other", Role.INSPECTOR)))
                .isInstanceOf(ReportNotFoundException.class);
        assertThat(pdfs.render(id, users.current("pdf.admin", Role.ADMIN)).content()).isNotEmpty();
    }

    @Test
    void downloadEndpoint() throws Exception {
        CurrentUser user = users.current("pdf.web", Role.INSPECTOR);
        ReportHeaderForm form = new ReportHeaderForm();
        form.setInspectedAt(LocalDateTime.of(2026, 9, 13, 9, 0));
        form.setAddress("Avenida Descarga 3");
        long id = reports.create(form, user);
        mvc.perform(get("/reports/{id}/pdf", id).with(users.login("pdf.web", Role.INSPECTOR)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("-avenida-descarga-3.pdf")));
        mvc.perform(get("/reports/{id}/pdf", id).with(users.login("pdf.web.other", Role.INSPECTOR)))
                .andExpect(status().isNotFound());
    }
}
