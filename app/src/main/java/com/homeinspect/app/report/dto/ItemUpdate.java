package com.homeinspect.app.report.dto;

import com.homeinspect.app.report.ItemStatus;
import com.homeinspect.app.report.Rating;
import com.homeinspect.app.report.Recommendation;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Autosave of one element (JSON body sent by the editor). */
public record ItemUpdate(
        ItemStatus status,
        Rating rating,
        @Size(max = 50) List<@Size(max = 200) String> materials,
        @Size(max = 300) String materialOther,
        @Size(max = 50) List<@Size(max = 200) String> limitations,
        @Size(max = 5000) String limitationText,
        @Size(max = 20000) String observations,
        Recommendation recommendation,
        @Size(max = 5000) String recommendationText) {
}
