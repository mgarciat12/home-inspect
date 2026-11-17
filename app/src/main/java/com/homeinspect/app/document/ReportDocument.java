package com.homeinspect.app.document;

import com.homeinspect.app.catalog.FieldType;
import com.homeinspect.app.report.ItemStatus;
import com.homeinspect.app.report.Occupancy;
import com.homeinspect.app.report.PropertyType;
import com.homeinspect.app.report.Rating;
import com.homeinspect.app.report.Recommendation;
import com.homeinspect.app.report.ReportStatus;
import com.homeinspect.app.report.ReportType;
import com.homeinspect.app.report.SoilCondition;
import com.homeinspect.app.report.Weather;
import com.homeinspect.app.settings.CompanyView;
import com.homeinspect.app.user.LicenseService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Complete content of a report, in reading order, for the HTML preview and the PDF.
 * Built inside a transaction from the entities; templates never see JPA objects.
 */
public record ReportDocument(
        long id,
        String number,
        ReportType type,
        ReportStatus status,
        Instant inspectedAt,
        Instant finalizedAt,
        Inspector inspector,
        Client client,
        Property property,
        Conditions conditions,
        Photo cover,
        CompanyView company,
        List<FindingGroup> findings,
        List<Section> sections) {

    public record Inspector(long profileId, String name, String email, boolean hasSignature,
                            List<LicenseService.License> licenses) {

        /** "John A. Smith" -> "JAS" (wind mitigation: initials on every page). */
        public String initials() {
            StringBuilder result = new StringBuilder();
            for (String word : name.trim().split("\\s+")) {
                if (!word.isEmpty() && Character.isLetter(word.charAt(0))) {
                    result.append(Character.toUpperCase(word.charAt(0)));
                }
            }
            return result.toString();
        }

        /** License printed on a form: the first one accepted for that type of report, else the first one. */
        public LicenseService.License licenseFor(ReportType type) {
            return licenses.stream().filter(l -> l.type().acceptedFor(type)).findFirst()
                    .orElse(licenses.isEmpty() ? null : licenses.getFirst());
        }
    }

    public record Client(String name, String email, String phone, String insuranceCompany, String policyNumber) {
    }

    public record Property(String address, String zip, String city, String county, String state, PropertyType type,
                           Integer yearBuilt, Integer areaSqft, Integer floors, Occupancy occupancy,
                           String peoplePresent) {

        /** "123 Main St, Miami, FL 33101". */
        public String fullAddress() {
            StringBuilder text = new StringBuilder(address);
            if (city != null) {
                text.append(", ").append(city);
            }
            String stateZip = String.join(" ", java.util.stream.Stream.of(state, zip)
                    .filter(java.util.Objects::nonNull).toList());
            if (!stateZip.isBlank()) {
                text.append(", ").append(stateZip);
            }
            return text.toString();
        }
    }

    public record Conditions(Weather weather, BigDecimal temperatureF, SoilCondition soil) {
    }

    public record Photo(long id, String caption, int width, int height, String url, String thumbnailUrl) {
    }

    /** Findings of one rating, most severe group first. */
    public record FindingGroup(Rating rating, List<Finding> findings) {
    }

    /** Element rated as a finding: reference, observation excerpt and first photo. */
    public record Finding(String reference, String sectionTitle, String itemTitle, long itemId, String sectionCode,
                          String excerpt, Recommendation recommendation, Photo photo) {

        /** "1.2 Tejado › Canalones y bajantes". */
        public String label() {
            return reference + " " + sectionTitle + " › " + itemTitle;
        }
    }

    public record Section(int number, String code, String title, boolean notApplicable, String inspectionMethod,
                          String generalComments, String generalLimitations, List<Item> items) {
    }

    public record Item(long id, String reference, String code, String title, ItemStatus status, Rating rating,
                       List<String> materials, String materialOther, List<String> limitations,
                       String limitationText, String observations, Recommendation recommendation,
                       String recommendationText, List<Photo> photos, Question question) {

        /** Answer of a form question (4-Point, wind mitigation). */
        public record Question(FieldType type, List<String> options, List<String> answers, String help) {

            public boolean checked(String option) {
                return answers.contains(option);
            }

            public String value() {
                return answers.isEmpty() ? null : String.join(", ", answers);
            }

            /** Short options printed on one line; long ones (wind mitigation answers) one per line. */
            public boolean compact() {
                return options.stream().allMatch(o -> o.length() <= 32);
            }
        }

        public boolean hasContent() {
            return question != null ? !question.answers().isEmpty() || observations != null || !photos.isEmpty()
                    : status != null || !materials.isEmpty() || materialOther != null || observations != null
                    || !photos.isEmpty();
        }

        /** Materials plus the free "other" text, for display. */
        public List<String> allMaterials() {
            if (materialOther == null) {
                return materials;
            }
            List<String> all = new java.util.ArrayList<>(materials);
            all.add(materialOther);
            return all;
        }
    }

    /** Element present but not inspected, with the reasons (section 468.8323, F.S.). */
    public record NotInspected(String reference, String sectionTitle, String itemTitle, List<String> reasons) {

        public String label() {
            return reference + " " + sectionTitle + " › " + itemTitle;
        }
    }

    /** Elements marked "not inspected", in report order. */
    public List<NotInspected> notInspected() {
        List<NotInspected> result = new java.util.ArrayList<>();
        for (Section section : sections) {
            if (section.notApplicable()) {
                continue;
            }
            for (Item item : section.items()) {
                if (item.status() == ItemStatus.NOT_INSPECTED) {
                    List<String> reasons = new java.util.ArrayList<>(item.limitations());
                    if (item.limitationText() != null) {
                        reasons.add(item.limitationText());
                    }
                    result.add(new NotInspected(item.reference(), section.title(), item.title(), reasons));
                }
            }
        }
        return result;
    }

    /** Item of a section by its catalog code (forms address their questions by code). */
    public Item item(String sectionCode, String itemCode) {
        return sections.stream().filter(s -> s.code().equals(sectionCode)).flatMap(s -> s.items().stream())
                .filter(i -> i.code().equals(itemCode)).findFirst().orElse(null);
    }

    public Section section(String code) {
        return sections.stream().filter(s -> s.code().equals(code)).findFirst().orElse(null);
    }

    /** Some item has comments (forms: "Additional comments / observations"). */
    public boolean hasItemComments() {
        return sections.stream().flatMap(s -> s.items().stream()).anyMatch(i -> i.observations() != null);
    }

    /** Every photo of the report in reading order, for the photo addendum of the forms. */
    public List<Photo> allPhotos() {
        return sections.stream().flatMap(s -> s.items().stream()).flatMap(i -> i.photos().stream()).toList();
    }

    public int findingCount() {
        return findings.stream().mapToInt(group -> group.findings().size()).sum();
    }
}
