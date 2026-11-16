package com.homeinspect.app.report;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ReportSectionRepository extends JpaRepository<ReportSection, Long> {

    @Query("""
            select s from ReportSection s
            join fetch s.report r join fetch r.inspector
            where s.id = :sectionId and r.id = :reportId
            """)
    Optional<ReportSection> findInReport(long reportId, long sectionId);
}
