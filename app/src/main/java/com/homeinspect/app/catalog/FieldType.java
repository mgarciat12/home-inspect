package com.homeinspect.app.catalog;

import java.util.List;

/**
 * Type of a form question (4-Point, wind mitigation). Answers are stored as text in
 * {@code report_item.answers}, in English, as printed on the official forms.
 */
public enum FieldType {
    TEXT, NUMBER, YEAR, DATE,
    YES_NO(List.of("Yes", "No")),
    YES_NO_NA(List.of("Yes", "No", "N/A")),
    /** One of the options of the catalog. */
    CHOICE,
    /** Any number of the options of the catalog ("check all that apply"). */
    MULTI,
    CONDITION(List.of("Satisfactory", "Unsatisfactory", "N/A")),
    /** Photo slot: answered "Attached" when a photo is uploaded, or "Not applicable". */
    PHOTO(List.of(FieldType.ATTACHED, FieldType.NOT_APPLICABLE));

    public static final String ATTACHED = "Attached";
    public static final String NOT_APPLICABLE = "Not applicable";

    private final List<String> fixedOptions;

    FieldType() {
        this(List.of());
    }

    FieldType(List<String> fixedOptions) {
        this.fixedOptions = fixedOptions;
    }

    /** Options of the question: fixed for yes/no and condition types, otherwise those of the catalog. */
    public List<String> options(List<String> catalogOptions) {
        return fixedOptions.isEmpty() ? catalogOptions : fixedOptions;
    }

    /** Answered with radio buttons / checkboxes (as opposed to a typed value). */
    public boolean hasOptions() {
        return this != TEXT && this != NUMBER && this != YEAR && this != DATE;
    }

    public boolean multiple() {
        return this == MULTI;
    }
}
