package com.homeinspect.app.report;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Report numbers per type and year ({@code 2026-0001}, {@code 4P-2026-0001}, {@code WM-2026-0001}),
 * atomic even with concurrent creations.
 */
@Component
@RequiredArgsConstructor
public class ReportNumberGenerator {

    private final JdbcTemplate jdbcTemplate;

    public String next(ReportType type, int year) {
        Integer value = jdbcTemplate.queryForObject("""
                INSERT INTO report_number_seq (report_type, year, last_value) VALUES (?, ?, 1)
                ON CONFLICT (report_type, year) DO UPDATE SET last_value = report_number_seq.last_value + 1
                RETURNING last_value
                """, Integer.class, type.name(), year);
        return "%s%d-%04d".formatted(type.numberPrefix(), year, value);
    }
}
