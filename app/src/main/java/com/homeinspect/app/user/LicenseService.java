package com.homeinspect.app.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Licenses of each user ("My profile"); every method works on the given user's own licenses. */
@Service
@RequiredArgsConstructor
public class LicenseService {

    public static final int MAX_LICENSES = 10;
    /** Florida license numbers: letters, digits, spaces, dots and hyphens (HI1234, CBC1263456...). */
    private static final Pattern NUMBER = Pattern.compile("[A-Za-z0-9][A-Za-z0-9 .\\-]{0,49}");

    private final InspectorLicenseRepository licenses;
    private final UserProfileRepository profiles;

    /** License for display: type and number, without entity. */
    public record License(long id, LicenseType type, String number) {
    }

    /** Rejected license: the code is a message key. */
    public static class InvalidLicenseException extends RuntimeException {
        private final String code;

        public InvalidLicenseException(String code) {
            super(code);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    @Transactional(readOnly = true)
    public List<License> list(long profileId) {
        return licenses.findByProfileIdOrderByPositionAscIdAsc(profileId).stream()
                .map(l -> new License(l.getId(), l.getType(), l.getNumber())).toList();
    }

    @Transactional
    public void add(long profileId, LicenseType type, String number) {
        String clean = number == null ? "" : number.strip().toUpperCase(Locale.ROOT);
        if (type == null || !NUMBER.matcher(clean).matches()) {
            throw new InvalidLicenseException("profile.license.invalid");
        }
        List<InspectorLicense> current = licenses.findByProfileIdOrderByPositionAscIdAsc(profileId);
        if (licenses.existsByProfileIdAndTypeAndNumberIgnoreCase(profileId, type, clean)) {
            throw new InvalidLicenseException("profile.license.duplicate");
        }
        if (current.size() >= MAX_LICENSES) {
            throw new InvalidLicenseException("profile.license.tooMany");
        }
        int position = current.stream().mapToInt(InspectorLicense::getPosition).max().orElse(0) + 10;
        licenses.save(new InspectorLicense(profiles.getReferenceById(profileId), type, clean, position));
    }

    @Transactional
    public void delete(long profileId, long licenseId) {
        licenses.findByIdAndProfileId(licenseId, profileId).ifPresent(licenses::delete);
    }
}
