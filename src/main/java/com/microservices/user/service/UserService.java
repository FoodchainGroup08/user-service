package com.microservices.user.service;

import com.microservices.user.dto.UpdateUserRequest;
import com.microservices.user.dto.UserResponse;
import com.microservices.user.entity.User;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.List;
import java.util.UUID;

public interface UserService extends UserDetailsService {

    UserResponse findById(UUID id);

    UserResponse findByEmail(String email);

    List<UserResponse> findAll();

    /**
     * Returns all users, optionally filtered by role.
     * @param role filter by this role; pass {@code null} to return all users
     */
    List<UserResponse> findAllUsers(User.Role role);

    /**
     * Activates or deactivates a user account.
     * @param id     the user's UUID
     * @param active {@code true} to activate, {@code false} to deactivate
     */
    void updateUserStatus(UUID id, boolean active);

    UserResponse updateUser(UUID id, UpdateUserRequest request);
}
