package com.homeinspect.app.settings;

import com.homeinspect.app.photo.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CompanySettingsService {

    static final String BRANDING_DIR = "branding";

    private final CompanySettingsRepository repository;
    private final FileStorage storage;

    @Transactional(readOnly = true)
    public CompanyView view() {
        CompanySettings s = repository.findById(CompanySettings.ID).orElseGet(CompanySettings::new);
        return new CompanyView(s.getCompanyName(), s.getLogoPath() != null, s.getAddress(), s.getPhone(),
                s.getEmail(), s.getWebsite(), s.getTaxId(), s.getLegalText(), s.getScopeText());
    }

    /** Company logo on disk, if one was uploaded. */
    @Transactional(readOnly = true)
    public Optional<Path> logoFile() {
        return repository.findById(CompanySettings.ID)
                .map(CompanySettings::getLogoPath)
                .map(name -> storage.resolve(BRANDING_DIR, name))
                .filter(Files::isRegularFile);
    }
}
