package com.homeinspect.auth.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AuthUserRepository extends JpaRepository<AuthUser, Long> {

    Optional<AuthUser> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    boolean existsByEmailIgnoreCase(String email);

    long countByRoleAndEnabledTrue(Role role);

    List<AuthUser> findAllByOrderByDisplayNameAsc();
}
