package com.homeinspect.app.catalog;

import com.homeinspect.app.report.ReportType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

/** A section of the Standard of Practice (roof, exterior...). */
@Entity
@Table(name = "section_def")
@Getter
@Setter
@NoArgsConstructor
public class SectionDef {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Report type whose catalog contains the section (codes are unique per type). */
    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, updatable = false)
    private ReportType type = ReportType.FULL;

    @Column(nullable = false, updatable = false)
    private String code;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private String title;

    private String description;

    /** The whole section may be marked "not applicable" (e.g. garage). */
    private boolean optional;

    /** Inspection methods offered for the section (e.g. how the roof was inspected). */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> methods = new ArrayList<>();

    private boolean active = true;
}
