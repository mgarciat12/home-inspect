package com.homeinspect.app.report;

import com.homeinspect.app.catalog.SectionDef;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "report_section")
@Getter
@Setter
@NoArgsConstructor
public class ReportSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", updatable = false)
    private Report report;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_def_id", updatable = false)
    private SectionDef definition;

    /** Optional sections (garage) can be excluded from the report. */
    @Column(name = "not_applicable")
    private boolean notApplicable;

    @Column(name = "inspection_method")
    private String inspectionMethod;

    @Column(name = "general_comments")
    private String generalComments;

    @Column(name = "general_limitations")
    private String generalLimitations;

    @OneToMany(mappedBy = "section", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ReportItem> items = new ArrayList<>();

    public ReportSection(SectionDef definition) {
        this.definition = definition;
    }

    public void addItem(ReportItem item) {
        item.setSection(this);
        items.add(item);
    }
}
