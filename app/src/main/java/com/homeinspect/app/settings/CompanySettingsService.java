package com.homeinspect.app.settings;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompanySettingsService {

    private final CompanySettingsRepository repository;

    @Transactional(readOnly = true)
    public CompanyView view() {
        CompanySettings s = repository.findById(CompanySettings.ID).orElseGet(CompanySettings::new);
        return new CompanyView(s.getCompanyName(), s.getLogoPath() != null, s.getAddress(), s.getPhone(),
                s.getEmail(), s.getWebsite(), s.getTaxId(), s.getLegalText(), s.getScopeText());
    }
}
