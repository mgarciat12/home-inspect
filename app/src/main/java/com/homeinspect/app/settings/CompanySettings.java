package com.homeinspect.app.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Company branding and standard texts of the reports (single row, id 1). */
@Entity
@Table(name = "company_settings")
@Getter
@Setter
@NoArgsConstructor
public class CompanySettings {

    public static final short ID = 1;

    @Id
    private Short id;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "logo_path")
    private String logoPath;

    private String address;
    private String phone;
    private String email;
    private String website;

    @Column(name = "tax_id")
    private String taxId;

    @Column(name = "legal_text")
    private String legalText;

    @Column(name = "scope_text")
    private String scopeText;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
