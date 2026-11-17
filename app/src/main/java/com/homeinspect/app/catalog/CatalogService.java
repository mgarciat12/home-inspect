package com.homeinspect.app.catalog;

import com.homeinspect.app.report.ReportType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Read access to the catalog: active structure for new reports, options and comments for the editor. */
@Service
@RequiredArgsConstructor
public class CatalogService {

    private final SectionDefRepository sections;
    private final ItemDefRepository items;
    private final LimitationDefRepository limitations;
    private final CommentTemplateRepository comments;

    @Transactional(readOnly = true)
    public List<SectionDef> activeSections(ReportType type) {
        return sections.findByTypeAndActiveTrueOrderByPositionAsc(type);
    }

    /** Types that can be used for new reports: those whose catalog has active sections. */
    @Transactional(readOnly = true)
    public Set<ReportType> availableTypes() {
        return Arrays.stream(ReportType.values())
                .filter(type -> !sections.findByTypeAndActiveTrueOrderByPositionAsc(type).isEmpty())
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ReportType.class)));
    }

    @Transactional(readOnly = true)
    public List<ItemDef> activeItems(SectionDef section) {
        return items.findBySectionAndActiveTrueOrderByPositionAsc(section);
    }

    @Transactional(readOnly = true)
    public List<String> commonLimitations() {
        return limitations.findAllByOrderByPositionAsc().stream().map(LimitationDef::getLabel).toList();
    }

    /** Comment library texts grouped by item id. */
    @Transactional(readOnly = true)
    public Map<Long, List<String>> commentsByItem(List<Long> itemIds) {
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        return comments.findByItemIds(itemIds).stream()
                .collect(Collectors.groupingBy(c -> c.getItem().getId(),
                        Collectors.mapping(CommentTemplate::getText, Collectors.toList())));
    }
}
