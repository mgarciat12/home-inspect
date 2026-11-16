package com.homeinspect.app.report;

import com.homeinspect.app.catalog.CatalogService;
import com.homeinspect.app.catalog.ItemDef;
import com.homeinspect.app.catalog.SectionDef;
import com.homeinspect.app.photo.PhotoService;
import com.homeinspect.app.photo.PhotoView;
import com.homeinspect.app.report.dto.ReportEditorView;
import com.homeinspect.app.report.dto.ReportEditorView.ItemView;
import com.homeinspect.app.report.dto.ReportEditorView.SectionNav;
import com.homeinspect.app.report.dto.ReportEditorView.SectionView;
import com.homeinspect.app.report.dto.ReportEditorView.Summary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Builds the editor view from the entities (inside the service transaction). */
@Component
@RequiredArgsConstructor
class ReportEditorMapper {

    private final CatalogService catalog;
    private final PhotoService photos;

    ReportEditorView editor(Report report, String sectionCode, boolean editable, boolean canReopen) {
        List<ReportSection> ordered = orderedSections(report);
        List<SectionNav> nav = new ArrayList<>();
        int completed = 0;
        int total = 0;
        for (int i = 0; i < ordered.size(); i++) {
            ReportSection section = ordered.get(i);
            List<ReportItem> sectionItems = orderedItems(section);
            int done = (int) sectionItems.stream().filter(ReportItem::isCompleted).count();
            nav.add(new SectionNav(section.getId(), section.getDefinition().getCode(), i + 1,
                    section.getDefinition().getTitle(), section.isNotApplicable(), done, sectionItems.size()));
            if (!section.isNotApplicable()) {
                completed += done;
                total += sectionItems.size();
            }
        }

        int index = 0;
        if (sectionCode != null) {
            for (int i = 0; i < ordered.size(); i++) {
                if (ordered.get(i).getDefinition().getCode().equals(sectionCode)) {
                    index = i;
                }
            }
        }
        SectionView current = ordered.isEmpty() ? null : section(report, ordered, index);

        Summary summary = new Summary(report.getId(), report.getNumber(), report.getStatus(), report.getAddress(),
                report.getCity(), report.getClientName(), report.getInspector().getDisplayName(),
                report.getInspectedAt(), report.getPropertyType(), report.getUpdatedAt(), completed, total);
        return new ReportEditorView(summary, nav, current, editable, canReopen);
    }

    private SectionView section(Report report, List<ReportSection> ordered, int index) {
        ReportSection section = ordered.get(index);
        SectionDef definition = section.getDefinition();
        List<ReportItem> sectionItems = orderedItems(section);
        Map<Long, List<String>> comments = catalog.commentsByItem(
                sectionItems.stream().map(i -> i.getDefinition().getId()).toList());
        List<String> commonLimitations = catalog.commonLimitations();
        Map<Long, List<PhotoView>> itemPhotos = photos.byItems(report.getId(),
                sectionItems.stream().map(ReportItem::getId).toList(), report.getCoverPhotoId());

        List<ItemView> itemViews = new ArrayList<>();
        for (int i = 0; i < sectionItems.size(); i++) {
            ReportItem item = sectionItems.get(i);
            ItemDef itemDef = item.getDefinition();
            itemViews.add(new ItemView(item.getId(), (index + 1) + "." + (i + 1), itemDef.getTitle(),
                    item.getStatus(), item.getRating(),
                    union(itemDef.getMaterials(), item.getMaterials()), item.getMaterials(), item.getMaterialOther(),
                    union(union(commonLimitations, itemDef.getLimitations()), item.getLimitations()),
                    item.getLimitations(), item.getLimitationText(),
                    item.getObservations(), item.getRecommendation(), item.getRecommendationText(),
                    comments.getOrDefault(itemDef.getId(), List.of()), item.isCompleted(),
                    itemPhotos.getOrDefault(item.getId(), List.of())));
        }
        String previous = index > 0 ? ordered.get(index - 1).getDefinition().getCode() : null;
        String next = index < ordered.size() - 1 ? ordered.get(index + 1).getDefinition().getCode() : null;
        List<String> methods = union(definition.getMethods(),
                section.getInspectionMethod() == null ? List.of() : List.of(section.getInspectionMethod()));
        return new SectionView(section.getId(), definition.getCode(), index + 1, definition.getTitle(),
                definition.getDescription(), definition.isOptional(), section.isNotApplicable(), methods,
                section.getInspectionMethod(), section.getGeneralComments(), section.getGeneralLimitations(),
                itemViews, previous, next);
    }

    static List<ReportSection> orderedSections(Report report) {
        return report.getSections().stream()
                .sorted(Comparator.comparingInt((ReportSection s) -> s.getDefinition().getPosition())
                        .thenComparing(ReportSection::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    static List<ReportItem> orderedItems(ReportSection section) {
        return section.getItems().stream()
                .sorted(Comparator.comparingInt((ReportItem i) -> i.getDefinition().getPosition())
                        .thenComparing(ReportItem::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /** Catalog options plus values already chosen that are no longer in the catalog. */
    private static List<String> union(List<String> options, List<String> selected) {
        LinkedHashSet<String> all = new LinkedHashSet<>(options);
        all.addAll(selected);
        return List.copyOf(all);
    }
}
