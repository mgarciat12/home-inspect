package com.homeinspect.app.summary;

import com.homeinspect.app.document.ReportDocument.Finding;
import com.homeinspect.app.document.ReportDocument.FindingGroup;
import com.homeinspect.app.document.ReportDocument.Item;
import com.homeinspect.app.document.ReportDocument.Section;
import com.homeinspect.app.report.Rating;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * "Findings summary": every element rated as safety hazard, repair, specialist evaluation or
 * maintenance, grouped by severity (safety hazards first), with its reference, a short observation
 * and its first photo. Sections marked as not applicable are left out.
 */
@Service
public class FindingsSummaryService {

    static final int EXCERPT_LENGTH = 220;

    public List<FindingGroup> summarize(List<Section> sections) {
        List<FindingGroup> groups = new ArrayList<>();
        for (Rating rating : Rating.findingsBySeverity()) {
            List<Finding> findings = new ArrayList<>();
            for (Section section : sections) {
                if (section.notApplicable()) {
                    continue;
                }
                for (Item item : section.items()) {
                    if (item.rating() == rating) {
                        findings.add(new Finding(item.reference(), section.title(), item.title(), item.id(),
                                section.code(), excerpt(item.observations()), item.recommendation(),
                                item.photos().isEmpty() ? null : item.photos().getFirst()));
                    }
                }
            }
            if (!findings.isEmpty()) {
                groups.add(new FindingGroup(rating, findings));
            }
        }
        return groups;
    }

    /** First characters of the observations, cut at a word boundary. */
    static String excerpt(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String flat = text.strip().replaceAll("\\s+", " ");
        if (flat.length() <= EXCERPT_LENGTH) {
            return flat;
        }
        int cut = flat.lastIndexOf(' ', EXCERPT_LENGTH);
        return flat.substring(0, cut > EXCERPT_LENGTH / 2 ? cut : EXCERPT_LENGTH).replaceAll("[\\s,;:.]+$", "") + "…";
    }
}
