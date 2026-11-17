package com.homeinspect.app.report;

import java.util.Comparator;
import java.util.List;

/**
 * Rating of an element. {@link #severity} orders the findings summary (safety hazards first);
 * SATISFACTORY is not a finding. Labels: messages.properties ({@code enum.Rating.*}).
 */
public enum Rating {
    SATISFACTORY(0, "success"),
    MAINTENANCE(5, "info"),
    REPAIR(2, "warning"),
    SAFETY_HAZARD(1, "danger"),
    SPECIALIST(3, "primary"),
    /** Near the end of its service life (section 468.8323, F.S.). */
    END_OF_LIFE(4, "secondary");

    private final int severity;
    private final String color;

    Rating(int severity, String color) {
        this.severity = severity;
        this.color = color;
    }

    public int severity() {
        return severity;
    }

    /** Bootstrap contextual color used for the badge. */
    public String color() {
        return color;
    }

    public boolean isFinding() {
        return this != SATISFACTORY;
    }

    /** Findings from most to least severe. */
    public static List<Rating> findingsBySeverity() {
        return java.util.Arrays.stream(values()).filter(Rating::isFinding)
                .sorted(Comparator.comparingInt(Rating::severity)).toList();
    }
}
