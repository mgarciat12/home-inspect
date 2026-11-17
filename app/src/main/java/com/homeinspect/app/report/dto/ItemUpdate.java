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
        @Size(max = 5000) String recommendationText,
        /** Answers of a form question (4-Point, wind mitigation); ignored for standard items. */
        @Size(max = 50) List<@Size(max = 500) String> answers) {

    /** Standard inspection item (no form answers). */
    public ItemUpdate(ItemStatus status, Rating rating, List<String> materials, String materialOther,
                      List<String> limitations, String limitationText, String observations,
                      Recommendation recommendation, String recommendationText) {
        this(status, rating, materials, materialOther, limitations, limitationText, observations, recommendation,
                recommendationText, null);
    }

    /** Form question: answers and comments. */
    public static ItemUpdate answers(List<String> answers, String comments) {
        return new ItemUpdate(null, null, null, null, null, null, comments, null, null, answers);
    }
}
