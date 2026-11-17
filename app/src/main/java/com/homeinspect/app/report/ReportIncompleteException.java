package com.homeinspect.app.report;

import java.util.List;

/** The report cannot be finalized yet: each problem names an item and what is missing. */
public class ReportIncompleteException extends RuntimeException {

    /** {@code code} is a message key (reason not stated, answer required, explanation, photo). */
    public record Problem(String item, String code) {
    }

    private final List<Problem> problems;

    public ReportIncompleteException(List<Problem> problems) {
        super("Report incomplete: " + problems);
        this.problems = List.copyOf(problems);
    }

    public List<Problem> problems() {
        return problems;
    }
}
