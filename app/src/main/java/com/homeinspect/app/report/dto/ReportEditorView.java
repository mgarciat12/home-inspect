package com.homeinspect.app.report.dto;

import com.homeinspect.app.catalog.FieldType;
import com.homeinspect.app.photo.PhotoView;
import com.homeinspect.app.report.ItemStatus;
import com.homeinspect.app.report.PropertyType;
import com.homeinspect.app.report.Rating;
import com.homeinspect.app.report.Recommendation;
import com.homeinspect.app.report.ReportStatus;
import com.homeinspect.app.report.ReportType;

import java.time.Instant;
import java.util.List;

/** Everything the editor page needs, without exposing JPA entities. */
public record ReportEditorView(
        Summary report,
        List<SectionNav> sections,
        SectionView current,
        boolean editable,
        boolean canReopen) {

    public record Summary(long id, String number, ReportType type, ReportStatus status, String address, String city,
                          String clientName, String inspectorName, Instant inspectedAt, PropertyType propertyType,
                          Instant updatedAt, int completed, int total) {

        public int percent() {
            return total == 0 ? 0 : Math.round(completed * 100f / total);
        }
    }

    /** Another report of the same visit. */
    public record Sibling(long id, String number, ReportType type, ReportStatus status) {
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

    public record ItemView(long id, String code, String reference, String title,
                           ItemStatus status, Rating rating,
                           List<String> materialOptions, List<String> materials, String materialOther,
                           List<String> limitationOptions, List<String> limitations, String limitationText,
                           String observations, Recommendation recommendation, String recommendationText,
                           List<String> comments, boolean completed, List<PhotoView> photos,
                           Question question) {

        /** Form question (4-Point, wind mitigation); null for standard inspection items. */
        public record Question(FieldType type, List<String> options, List<String> answers, boolean required,
                               List<String> explainOn, boolean photoRequired, String help) {

            /** Short options shown as buttons; long ones (wind mitigation answers) as a vertical list. */
            public boolean compact() {
                return options.stream().allMatch(o -> o.length() <= 32);
            }

            /** First answer, for typed values (text, number, year, date). */
            public String value() {
                return answers.isEmpty() ? null : answers.getFirst();
            }
        }
    }
}
