package com.homeinspect.app.catalog;

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

import java.util.ArrayList;
import java.util.List;

/** An element inspected inside a section (e.g. roof › gutters). */
@Entity
@Table(name = "item_def")
@Getter
@Setter
@NoArgsConstructor
public class ItemDef {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", updatable = false)
    private SectionDef section;

    @Column(nullable = false, updatable = false)
    private String code;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private String title;

    /** Description / material options (multiple choice). */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> materials = new ArrayList<>();

    /** Extra limitations for this item, besides the common ones. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> limitations = new ArrayList<>();

    private boolean active = true;

    /** Form question type (4-Point, wind mitigation); null for a standard inspection item. */
    @Enumerated(EnumType.STRING)
    @Column(name = "field_type")
    private FieldType fieldType;

    /** Options of CHOICE / MULTI questions. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> options = new ArrayList<>();

    private boolean required;

    /** Answers that require an explanation in the comments. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "explain_on", columnDefinition = "text[]")
    private List<String> explainOn = new ArrayList<>();

    @Column(name = "photo_required")
    private boolean photoRequired;

    /** Instructions shown under the question. */
    private String help;

    public boolean isQuestion() {
        return fieldType != null;
    }

    /** The comments of the catalog file were loaded into the comment library (only once). */
    @Column(name = "comments_seeded")
    private boolean commentsSeeded = true;
}
