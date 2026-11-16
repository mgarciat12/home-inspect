package com.homeinspect.app.report;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Report numbers per year ({@code 2026-0001}), atomic even with concurrent creations. */
@Component
@RequiredArgsConstructor
public class ReportNumberGenerator {

    private final JdbcTemplate jdbcTemplate;

    public String next(int year) {
        Integer value = jdbcTemplate.queryForObject("""
                INSERT INTO report_number_seq (year, last_value) VALUES (?, 1)
                ON CONFLICT (year) DO UPDATE SET last_value = report_number_seq.last_value + 1
                RETURNING last_value
                """, Integer.class, year);
        return "%d-%04d".formatted(year, value);
    }
}
