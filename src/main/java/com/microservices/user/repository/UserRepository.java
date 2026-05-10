package com.microservices.user.repository;

import com.microservices.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByOauth2ProviderAndOauth2ProviderId(String provider, String providerId);

    List<User> findByRole(User.Role role);
}
