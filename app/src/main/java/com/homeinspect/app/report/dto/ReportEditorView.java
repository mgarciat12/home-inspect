package com.homeinspect.app.report.dto;

import com.homeinspect.app.report.ItemStatus;
import com.homeinspect.app.report.PropertyType;
import com.homeinspect.app.report.Rating;
import com.homeinspect.app.report.Recommendation;
import com.homeinspect.app.report.ReportStatus;

import java.time.Instant;
import java.util.List;

/** Everything the editor page needs, without exposing JPA entities. */
public record ReportEditorView(
        Summary report,
        List<SectionNav> sections,
        SectionView current,
        boolean editable,
        boolean canReopen) {

    public record Summary(long id, String number, ReportStatus status, String address, String city,
                          String clientName, String inspectorName, Instant inspectedAt, PropertyType propertyType,
                          Instant updatedAt, int completed, int total) {

        public int percent() {
            return total == 0 ? 0 : Math.round(completed * 100f / total);
        }
    }

    /** Entry of the section navigation, with its progress. */
    public record SectionNav(long id, String code, int number, String title, boolean notApplicable,
                             int completed, int total) {

        public boolean done() {
            return notApplicable || (total > 0 && completed == total);
        }
    }

    public record SectionView(long id, String code, int number, String title, String description,
                              boolean optional, boolean notApplicable, List<String> methods,
                              String inspectionMethod, String generalComments, String generalLimitations,
                              List<ItemView> items, String previousCode, String nextCode) {
    }

    public record ItemView(long id, String reference, String title,
                           ItemStatus status, Rating rating,
                           List<String> materialOptions, List<String> materials, String materialOther,
                           List<String> limitationOptions, List<String> limitations, String limitationText,
                           String observations, Recommendation recommendation, String recommendationText,
                           List<String> comments, boolean completed) {
    }
}
