package com.homeinspect.app.catalog;

import com.homeinspect.app.report.ReportType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SectionDefRepository extends JpaRepository<SectionDef, Long> {

    Optional<SectionDef> findByTypeAndCode(ReportType type, String code);

    List<SectionDef> findByType(ReportType type);

    List<SectionDef> findByTypeAndActiveTrueOrderByPositionAsc(ReportType type);
}
