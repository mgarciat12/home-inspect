package com.homeinspect.app.settings;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CompanySettingsForm {

    @Size(max = 200)
    private String companyName;
    @Size(max = 300)
    private String address;
    @Size(max = 50)
    private String phone;
    @Email
    @Size(max = 255)
    private String email;
    @Size(max = 255)
    private String website;
    @Size(max = 50)
    private String taxId;
    @Size(max = 5000)
    private String legalText;
    @Size(max = 10000)
    private String scopeText;
}
