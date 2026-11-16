package com.homeinspect.app.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LimitationDefRepository extends JpaRepository<LimitationDef, Long> {

    List<LimitationDef> findAllByOrderByPositionAsc();
}
