package com.homeinspect.app.admin;

import com.homeinspect.app.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminUserForm {

    /** Only used when creating: the username never changes afterwards. */
    @Pattern(regexp = "[A-Za-z0-9._-]{3,50}", message = "{admin.user.usernameFormat}")
    private String username;

    @NotBlank
    @Size(max = 150)
    private String displayName;

    @Email
    @Size(max = 255)
    private String email;

    @NotNull
    private Role role = Role.INSPECTOR;

    private boolean enabled = true;
}
