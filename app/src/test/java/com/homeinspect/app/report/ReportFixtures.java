package com.homeinspect.app.report;

import com.homeinspect.app.report.dto.ItemUpdate;
import com.homeinspect.app.report.dto.ReportHeaderForm;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

final class ReportFixtures {

    private ReportFixtures() {
    }

    static ReportHeaderForm header(String address) {
        ReportHeaderForm form = new ReportHeaderForm();
        form.setInspectedAt(LocalDateTime.of(2026, 3, 14, 10, 30));
        form.setAddress(address);
        form.setCity("Madrid");
        form.setClientName("Cliente de " + address);
        form.setPropertyType(PropertyType.APARTMENT);
        form.setAreaM2(new BigDecimal("85.5"));
        form.setWeather(Weather.SUNNY);
        return form;
    }

    static ItemUpdate rated(Rating rating, String observations) {
        return new ItemUpdate(ItemStatus.INSPECTED, rating, List.of("Teja cerámica"), null,
                List.of("Altura o pendiente"), null, observations, Recommendation.REPAIR, null);
    }
}
