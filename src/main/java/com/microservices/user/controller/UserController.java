package com.microservices.user.controller;

import com.microservices.user.dto.UpdateUserRequest;
import com.microservices.user.dto.UserResponse;
import com.microservices.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(userService.findById(UUID.fromString(principal.getUsername())));
    }

    @GetMapping
    @PreAuthorize("hasRole('HEAD_OFFICE_ADMIN')")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.findAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HEAD_OFFICE_ADMIN', 'BRANCH_MANAGER')")
    public ResponseEntity<UserResponse> getUserById(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.findById(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('HEAD_OFFICE_ADMIN', 'BRANCH_MANAGER')")
    public ResponseEntity<UserResponse> updateUser(@PathVariable UUID id,
                                                   @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateUser(id, request));
    }
}
