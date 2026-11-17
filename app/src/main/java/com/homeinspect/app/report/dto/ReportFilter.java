package com.homeinspect.app.report.dto;

import com.homeinspect.app.report.ReportStatus;
import com.homeinspect.app.report.ReportType;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Search of the report list: text (number, address, client), type, status and inspection date range. */
public record ReportFilter(
        String q,
        ReportType type,
        ReportStatus status,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

    public static ReportFilter empty() {
        return new ReportFilter(null, null, null, null, null);
    }
}
