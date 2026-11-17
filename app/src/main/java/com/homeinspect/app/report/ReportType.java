package com.homeinspect.app.report;

/**
 * Kind of report (Florida). Each type has its own catalog ({@code catalog/<type>.yml}) and its own
 * numbering. Labels: messages.properties ({@code enum.ReportType.*}).
 */
public enum ReportType {

    /** Full home inspection, Florida Standards of Practice (rules 61-30.801 to 61-30.811, F.A.C.). */
    FULL("", "home-inspection"),
    /** 4-Point inspection for insurance (Citizens form Insp4pt 03 25). */
    FOUR_POINT("4P-", "4-point"),
    /** Wind mitigation (Uniform Mitigation Verification Inspection Form OIR-B1-1802 Rev. 04/26). */
    WIND_MITIGATION("WM-", "wind-mitigation");

    private final String numberPrefix;
    private final String fileSlug;

    ReportType(String numberPrefix, String fileSlug) {
        this.numberPrefix = numberPrefix;
        this.fileSlug = fileSlug;
    }

    /** Start of the PDF file name ({@code wind-mitigation-2026-0001-...pdf}). */
    public String fileSlug() {
        return fileSlug;
    }

    /** Prefix of the report number ({@code 4P-2026-0001}); empty for full inspections. */
    public String numberPrefix() {
        return numberPrefix;
    }

    /** Name of the catalog file in {@code classpath:catalog/}. */
    public String catalogFile() {
        return name().toLowerCase(java.util.Locale.ROOT).replace('_', '-') + ".yml";
    }
}
