package com.homeinspect.app.user;

import com.homeinspect.app.report.ReportType;

/**
 * Florida licenses accepted by the 4-Point and wind mitigation forms, with the statute that
 * defines them. Labels: messages.properties ({@code enum.LicenseType.*}).
 */
public enum LicenseType {
    HOME_INSPECTOR("468.8314"),
    BUILDING_CODE_INSPECTOR("468.607"),
    GENERAL_CONTRACTOR("489.111"),
    BUILDING_CONTRACTOR("489.111"),
    RESIDENTIAL_CONTRACTOR("489.111"),
    ROOFING_CONTRACTOR("489.111"),
    PLUMBING_CONTRACTOR("489.111"),
    MECHANICAL_CONTRACTOR("489.111"),
    ELECTRICAL_CONTRACTOR("489.511"),
    PROFESSIONAL_ENGINEER("471.015"),
    ARCHITECT("481.213"),
    OTHER("");

    private final String statute;

    LicenseType(String statute) {
        this.statute = statute;
    }

    /**
     * Licenses that may sign the whole form: 4-Point (Citizens) and uniform mitigation verification
     * (section 627.711(2), F.S.). Trade contractors may only sign their own section of a 4-Point.
     */
    public boolean acceptedFor(ReportType type) {
        return switch (type) {
            case FULL -> this == HOME_INSPECTOR;
            case FOUR_POINT -> this == HOME_INSPECTOR || this == BUILDING_CODE_INSPECTOR || this == GENERAL_CONTRACTOR
                    || this == BUILDING_CONTRACTOR || this == RESIDENTIAL_CONTRACTOR;
            case WIND_MITIGATION -> this != OTHER && this != ROOFING_CONTRACTOR && this != PLUMBING_CONTRACTOR
                    && this != MECHANICAL_CONTRACTOR && this != ELECTRICAL_CONTRACTOR;
        };
    }

    /** Section of the Florida Statutes ({@code 468.8314}); empty for {@link #OTHER}. */
    public String statute() {
        return statute;
    }
}
