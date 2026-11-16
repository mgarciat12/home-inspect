package com.homeinspect.app.security;

import com.homeinspect.app.user.Role;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileSyncingOidcUserServiceTest {

    @Test
    void mapsTheRolesClaim() {
        assertThat(ProfileSyncingOidcUserService.mainRole(List.of("INSPECTOR"))).contains(Role.INSPECTOR);
        assertThat(ProfileSyncingOidcUserService.mainRole(List.of("INSPECTOR", "ADMIN"))).contains(Role.ADMIN);
        assertThat(ProfileSyncingOidcUserService.mainRole(List.of("OTHER"))).isEmpty();
        assertThat(ProfileSyncingOidcUserService.mainRole(null)).isEmpty();
        assertThat(ProfileSyncingOidcUserService.mainRole("ADMIN")).isEmpty();
    }
}
