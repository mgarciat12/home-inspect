package com.homeinspect.app.report;

import com.homeinspect.app.user.UserProfile;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "report")
@Getter
@Setter
@NoArgsConstructor
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private String number;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, updatable = false)
    private ReportType type = ReportType.FULL;

    /** Reports created together for the same visit share this id (null for a single report). */
    @Column(name = "visit_id", updatable = false)
    private java.util.UUID visitId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportStatus status = ReportStatus.DRAFT;

    @Column(name = "inspected_at", nullable = false)
    private Instant inspectedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inspector_id")
    private UserProfile inspector;

    // ---- client ----
    @Column(name = "client_name")
    private String clientName;
    @Column(name = "client_email")
    private String clientEmail;
    @Column(name = "client_phone")
    private String clientPhone;
    @Column(name = "insurance_company")
    private String insuranceCompany;
    @Column(name = "policy_number")
    private String policyNumber;

    // ---- property ----
    @Column(nullable = false)
    private String address;
    @Column(name = "postal_code")
    private String postalCode;
    private String city;
    private String county;
    private String state;
    @Enumerated(EnumType.STRING)
    @Column(name = "property_type")
    private PropertyType propertyType;
    @Column(name = "year_built")
    private Integer yearBuilt;
    @Column(name = "area_sqft")
    private Integer areaSqft;
    private Integer floors;
    @Enumerated(EnumType.STRING)
    private Occupancy occupancy;
    @Column(name = "people_present")
    private String peoplePresent;

    // ---- conditions ----
    @Enumerated(EnumType.STRING)
    private Weather weather;
    @Column(name = "temperature_f")
    private BigDecimal temperatureF;
    @Enumerated(EnumType.STRING)
    @Column(name = "soil_condition")
    private SoilCondition soilCondition;

    @Column(name = "cover_photo_id")
    private Long coverPhotoId;

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ReportSection> sections = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at")
    private Instant updatedAt;
    @Column(name = "finalized_at")
    private Instant finalizedAt;

    public boolean isFinal() {
        return status == ReportStatus.FINAL;
    }

    public void addSection(ReportSection section) {
        section.setReport(this);
        sections.add(section);
    }

    /** Marks the report as changed (child entities do not bump the parent automatically). */
    public void touch() {
        updatedAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
