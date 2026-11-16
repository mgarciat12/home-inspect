package com.homeinspect.app.document;

import com.homeinspect.app.document.ReportDocument.Photo;
import com.homeinspect.app.photo.PhotoService;
import com.homeinspect.app.photo.PhotoView;
import com.homeinspect.app.report.Report;
import com.homeinspect.app.report.ReportAccessGuard;
import com.homeinspect.app.report.ReportEditorMapper;
import com.homeinspect.app.report.ReportItem;
import com.homeinspect.app.report.ReportNotFoundException;
import com.homeinspect.app.report.ReportRepository;
import com.homeinspect.app.report.ReportSection;
import com.homeinspect.app.security.CurrentUser;
import com.homeinspect.app.settings.CompanySettingsService;
import com.homeinspect.app.summary.FindingsSummaryService;
import com.homeinspect.app.user.SignatureService;
import com.homeinspect.app.user.UserProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Builds the {@link ReportDocument} of a report the user is allowed to see. */
@Service
@RequiredArgsConstructor
public class ReportDocumentService {

    private final ReportRepository reports;
    private final ReportAccessGuard guard;
    private final PhotoService photos;
    private final FindingsSummaryService summary;
    private final CompanySettingsService company;
    private final SignatureService signatures;

    @Transactional(readOnly = true)
    public ReportDocument build(long reportId, CurrentUser user) {
        Report report = reports.findById(reportId).orElseThrow(ReportNotFoundException::new);
        guard.checkView(report, user);

        List<ReportSection> ordered = ReportEditorMapper.orderedSections(report);
        List<Long> itemIds = ordered.stream().flatMap(s -> s.getItems().stream()).map(ReportItem::getId).toList();
        Map<Long, List<PhotoView>> itemPhotos = photos.byItems(reportId, itemIds, report.getCoverPhotoId());

        Photo cover = null;
        List<ReportDocument.Section> sections = new ArrayList<>();
        for (int s = 0; s < ordered.size(); s++) {
            ReportSection section = ordered.get(s);
            List<ReportItem> sectionItems = ReportEditorMapper.orderedItems(section);
            List<ReportDocument.Item> items = new ArrayList<>();
            for (int i = 0; i < sectionItems.size(); i++) {
                ReportItem item = sectionItems.get(i);
                List<Photo> itemPhotoList = itemPhotos.getOrDefault(item.getId(), List.of()).stream()
                        .map(ReportDocumentService::photo).toList();
                for (PhotoView view : itemPhotos.getOrDefault(item.getId(), List.of())) {
                    if (view.cover()) {
                        cover = photo(view);
                    }
                }
                items.add(new ReportDocument.Item(item.getId(), (s + 1) + "." + (i + 1),
                        item.getDefinition().getTitle(), item.getStatus(), item.getRating(),
                        List.copyOf(item.getMaterials()), item.getMaterialOther(), List.copyOf(item.getLimitations()),
                        item.getLimitationText(), item.getObservations(), item.getRecommendation(),
                        item.getRecommendationText(), itemPhotoList));
            }
            sections.add(new ReportDocument.Section(s + 1, section.getDefinition().getCode(),
                    section.getDefinition().getTitle(), section.isNotApplicable(), section.getInspectionMethod(),
                    section.getGeneralComments(), section.getGeneralLimitations(), items));
        }

        UserProfile inspector = report.getInspector();
        return new ReportDocument(report.getId(), report.getNumber(), report.getStatus(), report.getInspectedAt(),
                report.getFinalizedAt(),
                new ReportDocument.Inspector(inspector.getId(), inspector.getDisplayName(), inspector.getEmail(),
                        signatures.file(inspector.getId()).isPresent()),
                new ReportDocument.Client(report.getClientName(), report.getClientEmail(), report.getClientPhone()),
                new ReportDocument.Property(report.getAddress(), report.getPostalCode(), report.getCity(),
                        report.getProvince(), report.getPropertyType(), report.getYearBuilt(), report.getAreaM2(),
                        report.getFloors(), report.getOccupancy(), report.getPeoplePresent()),
                new ReportDocument.Conditions(report.getWeather(), report.getTemperatureC(), report.getSoilCondition()),
                cover, company.view(), summary.summarize(sections), sections);
    }

    private static Photo photo(PhotoView view) {
        return new Photo(view.id(), view.caption(), view.width(), view.height(), view.url(), view.thumbnailUrl());
    }
}
