package com.homeinspect.auth.web;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChangePasswordForm {

    @NotBlank
    private String currentPassword;

    @NotBlank
    private String newPassword;

    @NotBlank
    private String confirmPassword;
}
