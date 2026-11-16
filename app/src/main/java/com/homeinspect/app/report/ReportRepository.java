package com.homeinspect.app.report;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ReportRepository extends JpaRepository<Report, Long>, JpaSpecificationExecutor<Report> {

    /** List page: loads the inspector in the same query. */
    @Override
    @EntityGraph(attributePaths = "inspector")
    Page<Report> findAll(Specification<Report> spec, Pageable pageable);
}
