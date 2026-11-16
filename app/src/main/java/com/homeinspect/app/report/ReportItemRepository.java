package com.homeinspect.app.report;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ReportItemRepository extends JpaRepository<ReportItem, Long> {

    @Query("""
            select i from ReportItem i
            join fetch i.section s join fetch s.report r join fetch r.inspector
            where i.id = :itemId and r.id = :reportId
            """)
    Optional<ReportItem> findInReport(long reportId, long itemId);
}
