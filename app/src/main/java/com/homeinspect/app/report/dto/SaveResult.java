package com.homeinspect.app.report.dto;

import java.time.Instant;

/** Answer to an autosave: progress of the element and its section. */
public record SaveResult(Instant savedAt, boolean itemCompleted, int sectionCompleted, int sectionTotal) {
}
