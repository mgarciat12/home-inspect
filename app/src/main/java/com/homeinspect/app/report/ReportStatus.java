package com.homeinspect.app.report;

/**
 * Lifecycle of a report. A final report is locked (only an admin can reopen it).
 * Labels: messages.properties ({@code enum.ReportStatus.*}).
 */
public enum ReportStatus {
    DRAFT, FINAL
}
