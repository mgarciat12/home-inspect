package com.homeinspect.app.report;

import com.homeinspect.app.report.dto.ItemUpdate;
import com.homeinspect.app.report.dto.ReportHeaderForm;

import java.time.LocalDateTime;
import java.util.List;

public final class ReportFixtures {

    private ReportFixtures() {
    }

    public static ReportHeaderForm header(String address) {
        ReportHeaderForm form = new ReportHeaderForm();
        form.setInspectedAt(LocalDateTime.of(2026, 3, 14, 10, 30));
        form.setAddress(address);
        form.setCity("Madrid");
        form.setClientName("Cliente de " + address);
        form.setPropertyType(PropertyType.APARTMENT);
        form.setAreaSqft(920);
        form.setWeather(Weather.SUNNY);
        return form;
    }

    static ItemUpdate rated(Rating rating, String observations) {
        return new ItemUpdate(ItemStatus.INSPECTED, rating, List.of("Concrete tile"), null,
                List.of("Roof slope / height unsafe"), null, observations, Recommendation.REPAIR, null);
    }
}
