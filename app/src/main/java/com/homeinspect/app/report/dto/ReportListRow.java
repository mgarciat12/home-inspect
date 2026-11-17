package com.homeinspect.app.report.dto;

import com.homeinspect.app.report.ReportStatus;
import com.homeinspect.app.report.ReportType;

import java.time.Instant;

public record ReportListRow(long id, String number, ReportType type, String address, String city, String clientName,
                            String inspectorName, Instant inspectedAt, ReportStatus status) {
}
