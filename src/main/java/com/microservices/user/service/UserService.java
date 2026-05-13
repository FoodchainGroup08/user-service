package com.microservices.user.service;

import com.microservices.user.dto.UpdateProfileRequest;
import com.microservices.user.dto.UpdateUserRequest;
import com.microservices.user.dto.UserResponse;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.List;
import java.util.UUID;

public interface UserService extends UserDetailsService {

    UserResponse findById(UUID id);

    UserResponse findByEmail(String email);

    List<UserResponse> findAll();

    UserResponse updateUser(UUID id, UpdateUserRequest request);

    UserResponse updateProfile(UUID id, UpdateProfileRequest request);

    UserResponse updateUserStatus(UUID id, boolean active);
}
