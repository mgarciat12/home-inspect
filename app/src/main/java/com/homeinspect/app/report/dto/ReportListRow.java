package com.homeinspect.app.report.dto;

import com.homeinspect.app.report.ReportStatus;

import java.time.Instant;

public record ReportListRow(long id, String number, String address, String city, String clientName,
                            String inspectorName, Instant inspectedAt, ReportStatus status) {
}
