package com.homeinspect.app.pdf;

import com.homeinspect.app.pdf.PdfService.Pdf;
import com.homeinspect.app.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.nio.charset.StandardCharsets;

/** Download of the report PDF. */
@Controller
@RequiredArgsConstructor
class PdfController {

    private final PdfService pdfs;
    private final CurrentUserProvider currentUser;

    @GetMapping("/reports/{reportId}/pdf")
    ResponseEntity<byte[]> download(@PathVariable long reportId) {
        Pdf pdf = pdfs.render(reportId, currentUser.get());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(pdf.fileName(), StandardCharsets.UTF_8).build().toString())
                .cacheControl(CacheControl.noStore())
                .body(pdf.content());
    }
}
