package com.homeinspect.app.report;

import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Locale;

/** Filters of the report list. */
final class ReportSpecifications {

    private ReportSpecifications() {
    }

    static Specification<Report> inspector(Long profileId) {
        return (root, query, cb) -> profileId == null ? null : cb.equal(root.get("inspector").get("id"), profileId);
    }

    static Specification<Report> type(ReportType type) {
        return (root, query, cb) -> type == null ? null : cb.equal(root.get("type"), type);
    }

    static Specification<Report> status(ReportStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    static Specification<Report> inspectedFrom(Instant from) {
        return (root, query, cb) -> from == null ? null : cb.greaterThanOrEqualTo(root.get("inspectedAt"), from);
    }

    static Specification<Report> inspectedBefore(Instant to) {
        return (root, query, cb) -> to == null ? null : cb.lessThan(root.get("inspectedAt"), to);
    }

    /** Number, address, city or client contains the text (case-insensitive). */
    static Specification<Report> text(String text) {
        return (root, query, cb) -> {
            if (text == null || text.isBlank()) {
                return null;
            }
            String pattern = "%" + text.trim().toLowerCase(Locale.ROOT)
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("number")), pattern, '\\'),
                    cb.like(cb.lower(root.get("address")), pattern, '\\'),
                    cb.like(cb.lower(cb.coalesce(root.get("city"), "")), pattern, '\\'),
                    cb.like(cb.lower(cb.coalesce(root.get("clientName"), "")), pattern, '\\'));
        };
    }
}
