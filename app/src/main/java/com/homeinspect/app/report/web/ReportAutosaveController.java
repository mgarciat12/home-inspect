package com.homeinspect.app.report.web;

import com.homeinspect.app.report.ReportService;
import com.homeinspect.app.report.dto.ItemUpdate;
import com.homeinspect.app.report.dto.SaveResult;
import com.homeinspect.app.report.dto.SectionUpdate;
import com.homeinspect.app.security.CurrentUserProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Autosave endpoints of the editor: each element and each section is saved on its own. */
@RestController
@RequestMapping("/reports/{reportId}")
@RequiredArgsConstructor
class ReportAutosaveController {

    private final ReportService reports;
    private final CurrentUserProvider currentUser;

    @PutMapping("/items/{itemId}")
    SaveResult saveItem(@PathVariable long reportId, @PathVariable long itemId, @Valid @RequestBody ItemUpdate update) {
        return reports.updateItem(reportId, itemId, update, currentUser.get());
    }

    @PutMapping("/sections/{sectionId}")
    SaveResult saveSection(@PathVariable long reportId, @PathVariable long sectionId,
                           @Valid @RequestBody SectionUpdate update) {
        return reports.updateSection(reportId, sectionId, update, currentUser.get());
    }
}
