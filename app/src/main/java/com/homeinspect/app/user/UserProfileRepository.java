package com.homeinspect.app.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    Optional<UserProfile> findBySubject(String subject);

    Optional<UserProfile> findByUsername(String username);

    List<UserProfile> findAllByOrderByDisplayNameAsc();
}
