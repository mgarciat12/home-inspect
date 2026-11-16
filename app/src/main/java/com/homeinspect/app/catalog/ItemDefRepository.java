package com.homeinspect.app.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ItemDefRepository extends JpaRepository<ItemDef, Long> {

    Optional<ItemDef> findBySectionAndCode(SectionDef section, String code);

    List<ItemDef> findBySectionOrderByPositionAsc(SectionDef section);

    List<ItemDef> findBySectionAndActiveTrueOrderByPositionAsc(SectionDef section);
}
