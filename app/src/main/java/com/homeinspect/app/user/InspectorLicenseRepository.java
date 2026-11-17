package com.homeinspect.app.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InspectorLicenseRepository extends JpaRepository<InspectorLicense, Long> {

    List<InspectorLicense> findByProfileIdOrderByPositionAscIdAsc(long profileId);

    Optional<InspectorLicense> findByIdAndProfileId(long id, long profileId);

    boolean existsByProfileIdAndTypeAndNumberIgnoreCase(long profileId, LicenseType type, String number);
}
