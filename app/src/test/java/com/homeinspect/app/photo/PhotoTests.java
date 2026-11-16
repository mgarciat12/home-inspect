package com.homeinspect.app.photo;

import com.homeinspect.app.TestUsers;
import com.homeinspect.app.TestcontainersConfiguration;
import com.homeinspect.app.photo.PhotoService.UploadResult;
import com.homeinspect.app.photo.PhotoService.Variant;
import com.homeinspect.app.report.ReportLockedException;
import com.homeinspect.app.report.ReportNotFoundException;
import com.homeinspect.app.report.ReportService;
import com.homeinspect.app.report.dto.ReportHeaderForm;
import com.homeinspect.app.report.dto.ReportEditorView.ItemView;
import com.homeinspect.app.security.CurrentUser;
import com.homeinspect.app.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestUsers.class})
class PhotoTests {

    @Autowired
    PhotoService photos;
    @Autowired
    ReportService reports;
    @Autowired
    FileStorage storage;
    @Autowired
    TestUsers users;
    @Autowired
    MockMvc mvc;

    record Fixture(CurrentUser user, long reportId, long itemId) {
    }

    Fixture report(String username) {
        CurrentUser user = users.current(username, Role.INSPECTOR);
        ReportHeaderForm form = new ReportHeaderForm();
        form.setInspectedAt(LocalDateTime.of(2026, 6, 1, 9, 0));
        form.setAddress("Calle Fotos " + username);
        long reportId = reports.create(form, user);
        long itemId = reports.editor(reportId, null, user).current().items().getFirst().id();
        return new Fixture(user, reportId, itemId);
    }

    static MultipartFile photo(String name) {
        return new MockMultipartFile("files", name, "image/jpeg", TestImages.jpegWithExif(TestImages.halves(2400, 1800), 1));
    }

    @Test
    void uploadStoresOptimizedAndThumbnailAndReportsInvalidFiles() {
        Fixture f = report("ph.upload");
        UploadResult result = photos.upload(f.reportId(), f.itemId(), List.of(photo("fachada.jpg"),
                new MockMultipartFile("files", "virus.jpg", "image/jpeg", "not an image".getBytes())), f.user());

        assertThat(result.photos()).hasSize(1);
        assertThat(result.errors()).singleElement().satisfies(e -> {
            assertThat(e.file()).isEqualTo("virus.jpg");
            assertThat(e.message()).contains("Formato no admitido");
        });
        PhotoView view = result.photos().getFirst();
        assertThat(view.width()).isEqualTo(1600);
        assertThat(photos.file(f.reportId(), view.id(), Variant.FULL, f.user()).path()).exists();
        assertThat(photos.file(f.reportId(), view.id(), Variant.THUMB, f.user()).path()).exists();

        ItemView item = reports.editor(f.reportId(), null, f.user()).current().items().getFirst();
        assertThat(item.photos()).extracting(PhotoView::id).containsExactly(view.id());
    }

    @Test
    void captionOrderCoverAndDelete() {
        Fixture f = report("ph.manage");
        List<PhotoView> uploaded = photos.upload(f.reportId(), f.itemId(),
                List.of(photo("a.jpg"), photo("b.jpg"), photo("c.jpg")), f.user()).photos();
        long a = uploaded.get(0).id();
        long b = uploaded.get(1).id();
        long c = uploaded.get(2).id();

        photos.updateCaption(f.reportId(), b, "  Grieta en el alero  ", f.user());
        photos.reorder(f.reportId(), f.itemId(), List.of(c, a), f.user());
        assertThat(photos.toggleCover(f.reportId(), b, f.user())).isTrue();

        List<PhotoView> listed = reports.editor(f.reportId(), null, f.user()).current().items().getFirst().photos();
        assertThat(listed).extracting(PhotoView::id).containsExactly(c, a, b);
        assertThat(listed.get(2).caption()).isEqualTo("Grieta en el alero");
        assertThat(listed.get(2).cover()).isTrue();

        Path full = photos.file(f.reportId(), b, Variant.FULL, f.user()).path();
        photos.delete(f.reportId(), b, f.user());
        assertThat(full).doesNotExist();
        assertThat(reports.editor(f.reportId(), null, f.user()).current().items().getFirst().photos())
                .extracting(PhotoView::id).containsExactly(c, a);
    }

    @Test
    void deletingTheReportRemovesItsPhotosFromDisk() {
        Fixture f = report("ph.cascade");
        photos.upload(f.reportId(), f.itemId(), List.of(photo("x.jpg")), f.user());
        Path dir = storage.photosOf(f.reportId());
        assertThat(dir).isDirectory();

        reports.delete(f.reportId(), f.user());
        assertThat(Files.exists(dir)).isFalse();
    }

    @Test
    void otherInspectorsCannotSeeOrChangePhotos() {
        Fixture f = report("ph.owner");
        long id = photos.upload(f.reportId(), f.itemId(), List.of(photo("p.jpg")), f.user()).photos().getFirst().id();
        CurrentUser other = users.current("ph.other", Role.INSPECTOR);

        assertThatThrownBy(() -> photos.file(f.reportId(), id, Variant.FULL, other)).isInstanceOf(ReportNotFoundException.class);
        assertThatThrownBy(() -> photos.delete(f.reportId(), id, other)).isInstanceOf(ReportNotFoundException.class);
        assertThatThrownBy(() -> photos.upload(f.reportId(), f.itemId(), List.of(photo("q.jpg")), other))
                .isInstanceOf(ReportNotFoundException.class);
        // A photo id from another report does not resolve either
        Fixture otherReport = report("ph.owner2");
        assertThatThrownBy(() -> photos.file(otherReport.reportId(), id, Variant.FULL, users.current("ph.owner2", Role.INSPECTOR)))
                .isInstanceOf(ReportNotFoundException.class);
    }

    @Test
    void finalReportsDoNotAcceptPhotos() {
        Fixture f = report("ph.final");
        reports.finalizeReport(f.reportId(), f.user());
        assertThatThrownBy(() -> photos.upload(f.reportId(), f.itemId(), List.of(photo("z.jpg")), f.user()))
                .isInstanceOf(ReportLockedException.class);
    }

    // ---- HTTP ----

    @Test
    void uploadAndDownloadThroughTheEndpoints() throws Exception {
        Fixture f = report("ph.web");
        var login = users.login("ph.web", Role.INSPECTOR);
        String body = mvc.perform(multipart("/reports/{r}/items/{i}/photos", f.reportId(), f.itemId())
                        .file((MockMultipartFile) photo("web.jpg")).with(login).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photos[0].thumbnailUrl").exists())
                .andReturn().getResponse().getContentAsString();
        int id = com.jayway.jsonpath.JsonPath.read(body, "$.photos[0].id");

        mvc.perform(get("/reports/{r}/photos/{p}/thumb", f.reportId(), id).with(login))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(header().string("Cache-Control", containsString("private")));
        mvc.perform(get("/reports/{r}/photos/{p}/full", f.reportId(), id).with(users.login("ph.web.other", Role.INSPECTOR)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/reports/{r}/photos/{p}/full", f.reportId(), id))
                .andExpect(status().is3xxRedirection()); // anonymous: to the login

        mvc.perform(get("/reports/{r}/edit", f.reportId()).with(login))
                .andExpect(content().string(containsString("/reports/" + f.reportId() + "/photos/" + id + "/thumb")))
                .andExpect(content().string(containsString("capture=\"environment\"")));
    }

    @Test
    void signatureUploadInTheProfile() throws Exception {
        var login = users.login("ph.signature", Role.INSPECTOR);
        mvc.perform(get("/profile/signature").with(login)).andExpect(status().isNotFound());
        MockMultipartFile file = new MockMultipartFile("file", "firma.png", "image/png",
                TestImages.png(new java.awt.image.BufferedImage(1200, 300, java.awt.image.BufferedImage.TYPE_INT_ARGB)));
        mvc.perform(multipart("/profile/signature").file(file).with(login).with(csrf()))
                .andExpect(redirectedUrl("/profile"));
        mvc.perform(get("/profile/signature").with(login))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));
        mvc.perform(get("/profile").with(login))
                .andExpect(content().string(containsString("Quitar firma")));
        mvc.perform(post("/profile/signature/delete").with(login).with(csrf())).andExpect(redirectedUrl("/profile"));
        mvc.perform(get("/profile/signature").with(login)).andExpect(status().isNotFound());
    }
}
