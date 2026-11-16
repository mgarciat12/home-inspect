package com.homeinspect.app.document;

import com.homeinspect.app.report.ItemStatus;
import com.homeinspect.app.report.Occupancy;
import com.homeinspect.app.report.PropertyType;
import com.homeinspect.app.report.Rating;
import com.homeinspect.app.report.Recommendation;
import com.homeinspect.app.report.ReportStatus;
import com.homeinspect.app.report.SoilCondition;
import com.homeinspect.app.report.Weather;
import com.homeinspect.app.settings.CompanyView;

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

    public record Inspector(long profileId, String name, String email, boolean hasSignature) {
    }

    public record Client(String name, String email, String phone) {
    }

    public record Property(String address, String postalCode, String city, String province, PropertyType type,
                           Integer yearBuilt, BigDecimal areaM2, Integer floors, Occupancy occupancy,
                           String peoplePresent) {

        /** "Street 1, 28001 Madrid (Madrid)". */
        public String fullAddress() {
            StringBuilder text = new StringBuilder(address);
            String place = String.join(" ", java.util.stream.Stream.of(postalCode, city)
                    .filter(java.util.Objects::nonNull).toList());
            if (!place.isBlank()) {
                text.append(", ").append(place);
            }
            if (province != null && !province.equalsIgnoreCase(city)) {
                text.append(" (").append(province).append(')');
            }
            return text.toString();
        }
    }

    public record Conditions(Weather weather, BigDecimal temperatureC, SoilCondition soil) {
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

    public record Item(long id, String reference, String title, ItemStatus status, Rating rating,
                       List<String> materials, String materialOther, List<String> limitations,
                       String limitationText, String observations, Recommendation recommendation,
                       String recommendationText, List<Photo> photos) {

        public boolean hasContent() {
            return status != null || !materials.isEmpty() || materialOther != null || observations != null
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

    public int findingCount() {
        return findings.stream().mapToInt(group -> group.findings().size()).sum();
    }
}
