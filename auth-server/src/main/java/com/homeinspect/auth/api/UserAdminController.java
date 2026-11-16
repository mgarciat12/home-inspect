package com.homeinspect.auth.api;

import com.homeinspect.auth.api.UserDtos.CreateUserRequest;
import com.homeinspect.auth.api.UserDtos.EnabledRequest;
import com.homeinspect.auth.api.UserDtos.RoleRequest;
import com.homeinspect.auth.api.UserDtos.UpdateUserRequest;
import com.homeinspect.auth.api.UserDtos.UserResponse;
import com.homeinspect.auth.api.UserDtos.UserWithPasswordResponse;
import com.homeinspect.auth.mfa.TotpService;
import com.homeinspect.auth.user.AuthUserService;
import com.homeinspect.auth.user.AuthUserService.NewUser;
import com.homeinspect.auth.user.AuthUserService.UserChanges;
import com.homeinspect.auth.user.AuthUserService.UserWithPassword;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;

/**
 * User administration API, used by the home-inspect app with a client-credentials token
 * (scope {@code users.admin}, enforced in the security configuration).
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
class UserAdminController {

    private final AuthUserService users;
    private final TotpService totp;
    private final Clock clock;

    @GetMapping
    List<UserResponse> list() {
        return users.findAll().stream().map(user -> UserResponse.of(user, clock.instant())).toList();
    }

    @GetMapping("/{id}")
    UserResponse get(@PathVariable long id) {
        return UserResponse.of(users.get(id), clock.instant());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    UserWithPasswordResponse create(@Valid @RequestBody CreateUserRequest request) {
        UserWithPassword created = users.create(
                new NewUser(request.username(), request.displayName(), request.email(), request.role()));
        return withPassword(created);
    }

    @PutMapping("/{id}")
    UserResponse update(@PathVariable long id, @Valid @RequestBody UpdateUserRequest request) {
        return UserResponse.of(users.update(id,
                new UserChanges(request.displayName(), request.email(), request.role(), request.enabled())),
                clock.instant());
    }

    @PutMapping("/{id}/enabled")
    UserResponse setEnabled(@PathVariable long id, @Valid @RequestBody EnabledRequest request) {
        return UserResponse.of(users.setEnabled(id, request.enabled()), clock.instant());
    }

    @PutMapping("/{id}/role")
    UserResponse changeRole(@PathVariable long id, @Valid @RequestBody RoleRequest request) {
        return UserResponse.of(users.changeRole(id, request.role()), clock.instant());
    }

    /** Generates a temporary password; the user must change it at next login. */
    @PostMapping("/{id}/password-reset")
    UserWithPasswordResponse resetPassword(@PathVariable long id) {
        return withPassword(users.resetPassword(id));
    }

    /** Removes the two-step verification of a user (e.g. lost phone); they can set it up again. */
    @PostMapping("/{id}/mfa-reset")
    UserResponse resetMfa(@PathVariable long id) {
        users.get(id); // 404 if unknown
        totp.reset(id);
        return UserResponse.of(users.get(id), clock.instant());
    }

    private UserWithPasswordResponse withPassword(UserWithPassword result) {
        return new UserWithPasswordResponse(UserResponse.of(result.user(), clock.instant()), result.temporaryPassword());
    }
}
