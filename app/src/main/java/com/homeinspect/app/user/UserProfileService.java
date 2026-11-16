package com.homeinspect.app.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserProfileRepository repository;
    private final Clock clock;

    /** Data received in the ID token at login. */
    public record LoginData(String subject, String username, String displayName, String email, Role role) {
    }

    /** Creates or refreshes the local profile of the user who has just logged in. */
    @Transactional
    public UserProfile syncFromLogin(LoginData data) {
        UserProfile profile = repository.findBySubject(data.subject()).orElseGet(() -> {
            UserProfile created = new UserProfile();
            created.setSubject(data.subject());
            return created;
        });
        profile.setUsername(data.username() != null ? data.username() : data.subject());
        profile.setDisplayName(data.displayName() != null ? data.displayName() : profile.getUsername());
        profile.setEmail(data.email());
        profile.setRole(data.role());
        profile.setActive(true);
        profile.setLastLoginAt(clock.instant());
        return repository.save(profile);
    }

    @Transactional(readOnly = true)
    public Optional<UserProfile> findBySubject(String subject) {
        return repository.findBySubject(subject);
    }

    @Transactional(readOnly = true)
    public UserProfile get(long id) {
        return repository.findById(id).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<UserProfile> findAll() {
        return repository.findAllByOrderByDisplayNameAsc();
    }

    /**
     * Mirrors a change made in the authorization server (admin panel). The next request of that
     * user ends their session, so the new state applies immediately (see ActiveUserFilter).
     */
    @Transactional
    public void mirrorAccountChange(String username, Boolean active, Role role, String displayName, String email) {
        repository.findByUsername(username).ifPresent(profile -> {
            if (active != null) {
                profile.setActive(active);
            }
            if (role != null) {
                profile.setRole(role);
            }
            if (displayName != null) {
                profile.setDisplayName(displayName);
            }
            if (email != null) {
                profile.setEmail(email.isBlank() ? null : email);
            }
        });
    }

    @Transactional
    public void updateSignature(long profileId, String signaturePath) {
        repository.findById(profileId).ifPresent(profile -> profile.setSignaturePath(signaturePath));
    }
}
