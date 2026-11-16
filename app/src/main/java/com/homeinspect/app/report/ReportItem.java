package com.homeinspect.app.report;

import com.homeinspect.app.catalog.ItemDef;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** One element of the report, filled with the same template for every element. */
@Entity
@Table(name = "report_item")
@Getter
@Setter
@NoArgsConstructor
public class ReportItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_section_id", updatable = false)
    private ReportSection section;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_def_id", updatable = false)
    private ItemDef definition;

    @Enumerated(EnumType.STRING)
    private ItemStatus status;

    @Enumerated(EnumType.STRING)
    private Rating rating;

    /** Selected description / material options (labels, so catalog changes do not alter old reports). */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> materials = new ArrayList<>();

    @Column(name = "material_other")
    private String materialOther;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> limitations = new ArrayList<>();

    @Column(name = "limitation_text")
    private String limitationText;

    private String observations;

    @Enumerated(EnumType.STRING)
    private Recommendation recommendation;

    @Column(name = "recommendation_text")
    private String recommendationText;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public ReportItem(ItemDef definition) {
        this.definition = definition;
    }

    /** An element counts as done once its state is set and, if inspected, it has a rating. */
    public boolean isCompleted() {
        return status != null && (status != ItemStatus.INSPECTED || rating != null);
    }
}
