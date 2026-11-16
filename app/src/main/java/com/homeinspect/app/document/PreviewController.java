package com.homeinspect.app.document;

import com.homeinspect.app.report.Rating;
import com.homeinspect.app.report.ReportNotFoundException;
import com.homeinspect.app.security.CurrentUserProvider;
import com.homeinspect.app.user.SignatureService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/** HTML preview of the report (same structure as the PDF) and the inspector's signature image. */
@Controller
@RequestMapping("/reports/{reportId}")
@RequiredArgsConstructor
class PreviewController {

    private final ReportDocumentService documents;
    private final SignatureService signatures;
    private final CurrentUserProvider currentUser;

    @GetMapping("/preview")
    String preview(@PathVariable long reportId, Model model) {
        model.addAttribute("doc", documents.build(reportId, currentUser.get()));
        model.addAttribute("ratings", Rating.values());
        return "reports/preview";
    }

    /** Signature of the report's inspector, for whoever can see the report (e.g. the admin). */
    @GetMapping("/signature")
    ResponseEntity<Resource> signature(@PathVariable long reportId) {
        ReportDocument doc = documents.build(reportId, currentUser.get());
        return signatures.file(doc.inspector().profileId())
                .map(path -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_PNG)
                        .cacheControl(CacheControl.noCache().cachePrivate())
                        .<Resource>body(new FileSystemResource(path)))
                .orElseThrow(ReportNotFoundException::new);
    }
}
