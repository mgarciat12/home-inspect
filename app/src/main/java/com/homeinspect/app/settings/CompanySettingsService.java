package com.homeinspect.app.settings;

import com.homeinspect.app.photo.FileStorage;
import com.homeinspect.app.photo.ImageProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

/** Company branding and standard texts (single row). Changes are admin-only. */
@Service
@RequiredArgsConstructor
public class CompanySettingsService {

    static final String BRANDING_DIR = "branding";
    private static final int LOGO_MAX_SIZE = 600;

    private final CompanySettingsRepository repository;
    private final FileStorage storage;
    private final ImageProcessor processor;

    @Transactional(readOnly = true)
    public CompanyView view() {
        CompanySettings s = repository.findById(CompanySettings.ID).orElseGet(CompanySettings::new);
        return new CompanyView(s.getCompanyName(), s.getLogoPath() != null, s.getAddress(), s.getPhone(),
                s.getEmail(), s.getWebsite(), s.getTaxId(), s.getLegalText(), s.getScopeText());
    }

    @Transactional(readOnly = true)
    public CompanySettingsForm form() {
        CompanySettings s = load();
        CompanySettingsForm form = new CompanySettingsForm();
        form.setCompanyName(s.getCompanyName());
        form.setAddress(s.getAddress());
        form.setPhone(s.getPhone());
        form.setEmail(s.getEmail());
        form.setWebsite(s.getWebsite());
        form.setTaxId(s.getTaxId());
        form.setLegalText(s.getLegalText());
        form.setScopeText(s.getScopeText());
        return form;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void update(CompanySettingsForm form) {
        CompanySettings s = load();
        s.setCompanyName(clean(form.getCompanyName()));
        s.setAddress(clean(form.getAddress()));
        s.setPhone(clean(form.getPhone()));
        s.setEmail(clean(form.getEmail()));
        s.setWebsite(clean(form.getWebsite()));
        s.setTaxId(clean(form.getTaxId()));
        s.setLegalText(clean(form.getLegalText()));
        s.setScopeText(clean(form.getScopeText()));
        repository.save(s);
    }

    /** Stores the logo as PNG (transparency kept, longest side 600 px). */
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void saveLogo(byte[] image) {
        byte[] png = processor.processPng(image, LOGO_MAX_SIZE);
        CompanySettings s = load();
        String fileName = "logo-" + UUID.randomUUID() + ".png";
        storage.write(storage.resolve(BRANDING_DIR, fileName), png);
        deleteAfterCommit(s.getLogoPath());
        s.setLogoPath(fileName);
        repository.save(s);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteLogo() {
        CompanySettings s = load();
        deleteAfterCommit(s.getLogoPath());
        s.setLogoPath(null);
        repository.save(s);
    }

    /** Company logo on disk, if one was uploaded. */
    @Transactional(readOnly = true)
    public Optional<Path> logoFile() {
        return repository.findById(CompanySettings.ID)
                .map(CompanySettings::getLogoPath)
                .map(name -> storage.resolve(BRANDING_DIR, name))
                .filter(Files::isRegularFile);
    }

    private CompanySettings load() {
        return repository.findById(CompanySettings.ID).orElseGet(() -> {
            CompanySettings created = new CompanySettings();
            created.setId(CompanySettings.ID);
            return created;
        });
    }

    private void deleteAfterCommit(String fileName) {
        if (fileName == null) {
            return;
        }
        Path path = storage.resolve(BRANDING_DIR, fileName);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storage.delete(path);
            }
        });
    }

    private static String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
