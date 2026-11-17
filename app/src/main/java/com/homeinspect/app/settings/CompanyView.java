package com.homeinspect.app.settings;

/** Company data shown on the report cover and footer. */
public record CompanyView(String name, boolean hasLogo, String address, String phone, String email,
                          String website, String taxId, String licenseNumber, String legalText, String scopeText) {

    public boolean hasContact() {
        return address != null || phone != null || email != null || website != null;
    }
}
