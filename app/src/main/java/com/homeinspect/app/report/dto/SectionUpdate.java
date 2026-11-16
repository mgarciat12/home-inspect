package com.homeinspect.app.report.dto;

import jakarta.validation.constraints.Size;

/** Autosave of the general fields of a section (JSON body sent by the editor). */
public record SectionUpdate(
        boolean notApplicable,
        @Size(max = 200) String inspectionMethod,
        @Size(max = 20000) String generalComments,
        @Size(max = 5000) String generalLimitations) {
}
