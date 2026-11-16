package com.homeinspect.auth.user;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Loads users from the database. It returns the standard Spring Security {@link User}, which the
 * JDBC authorization service knows how to serialize.
 */
@Service
@RequiredArgsConstructor
public class DatabaseUserDetailsService implements UserDetailsService {

    private final AuthUserRepository repository;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        AuthUser user = repository.findByUsernameIgnoreCase(username.trim())
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
        return User.withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .authorities(new SimpleGrantedAuthority(user.getRole().authority()))
                .disabled(!user.isEnabled())
                .accountLocked(user.isLocked(clock.instant()))
                .build();
    }
}
