package com.homeinspect.app.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
}
