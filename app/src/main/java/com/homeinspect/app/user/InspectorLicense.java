package com.homeinspect.app.user;

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

/** A professional license of a user, printed next to the signature. */
@Entity
@Table(name = "inspector_license")
@Getter
@Setter
@NoArgsConstructor
public class InspectorLicense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", updatable = false)
    private UserProfile profile;

    @Enumerated(EnumType.STRING)
    @Column(name = "license_type", nullable = false)
    private LicenseType type;

    @Column(nullable = false)
    private String number;

    @Column(nullable = false)
    private int position;

    public InspectorLicense(UserProfile profile, LicenseType type, String number, int position) {
        this.profile = profile;
        this.type = type;
        this.number = number;
        this.position = position;
    }
}
