package com.homeinspect.app.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SectionDefRepository extends JpaRepository<SectionDef, Long> {

    Optional<SectionDef> findByCode(String code);

    List<SectionDef> findAllByOrderByPositionAsc();

    List<SectionDef> findByActiveTrueOrderByPositionAsc();
}
