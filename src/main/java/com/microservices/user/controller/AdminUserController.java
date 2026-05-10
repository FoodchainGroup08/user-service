package com.microservices.user.controller;

import com.microservices.user.entity.User;
import com.microservices.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin - Users", description = "Admin-only endpoints for listing and managing user accounts")
@SecurityRequirement(name = "bearerAuth")
public class AdminUserController {

    private final UserService userService;

    // ---- Admin DTO -------------------------------------------------------

    public record AdminUserResponse(
            String id,
            String name,
            String email,
            String role,
            String status,
            String branchId
    ) {}

    // ---- Helper ----------------------------------------------------------

    /**
     * Prefers {@code X-User-Role} when present (API gateway). Otherwise uses the JWT-derived role from Spring Security
     * so calling user-service directly with {@code Authorization: Bearer} works.
     */
    private String resolveUserRole(String headerRole, Authentication authentication) {
        if (headerRole != null && !headerRole.isBlank()) {
            return headerRole.trim();
        }
        if (authentication == null) {
            return null;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .findFirst()
                .orElse(null);
    }

    private void assertAdmin(String userRole) {
        if (userRole == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        if (!isAdminRole(userRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
    }

    private boolean isAdminRole(String role) {
        return "HEAD_OFFICE_ADMIN".equals(role)
                || "OFFICE_ADMIN".equals(role)
                || "Admin".equals(role);
    }

    private AdminUserResponse toAdminResponse(com.microservices.user.dto.UserResponse u) {
        return new AdminUserResponse(
                u.getId() != null ? u.getId().toString() : null,
                u.getName(),
                u.getEmail(),
                u.getRole() != null ? u.getRole().getDisplayName() : null,
                u.isActive() ? "active" : "inactive",
                u.getBranchId() != null ? u.getBranchId().toString() : null
        );
    }

    // ---- Endpoints -------------------------------------------------------

    @Operation(
            summary = "List all users (Admin only)",
            description = "Returns all registered users. Optionally filter by role using the `role` query parameter " +
                          "(accepted values: Customer, Kitchen Staff, Branch Manager, Admin)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User list returned"),
            @ApiResponse(responseCode = "403", description = "Caller is not an Admin")
    })
    @GetMapping
    public ResponseEntity<List<AdminUserResponse>> getAllUsers(
            @Parameter(description = "X-User-Role header forwarded by the API gateway")
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @Parameter(description = "Filter by role display name, e.g. Customer")
            @RequestParam(required = false) String role,
            Authentication authentication) {

        assertAdmin(resolveUserRole(userRole, authentication));

        User.Role roleFilter = null;
        if (role != null && !role.isBlank()) {
            try {
                roleFilter = User.Role.fromDisplayName(role);
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown role: " + role);
            }
        }

        List<AdminUserResponse> result = userService.findAllUsers(roleFilter)
                .stream()
                .map(this::toAdminResponse)
                .toList();

        return ResponseEntity.ok(result);
    }

    @Operation(
            summary = "Get a user by ID (Admin only)",
            description = "Fetches a single user record by their UUID."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User found"),
            @ApiResponse(responseCode = "403", description = "Caller is not an Admin"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<AdminUserResponse> getUserById(
            @Parameter(description = "X-User-Role header forwarded by the API gateway")
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @PathVariable UUID id,
            Authentication authentication) {

        assertAdmin(resolveUserRole(userRole, authentication));
        return ResponseEntity.ok(toAdminResponse(userService.findById(id)));
    }

    @Operation(
            summary = "Update a user's active status (Admin only)",
            description = "Activates or deactivates a user account. Request body: `{ \"status\": \"active\" | \"inactive\" }`."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status updated, updated user returned"),
            @ApiResponse(responseCode = "400", description = "Invalid status value"),
            @ApiResponse(responseCode = "403", description = "Caller is not an Admin"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PatchMapping("/{id}/status")
    public ResponseEntity<AdminUserResponse> updateUserStatus(
            @Parameter(description = "X-User-Role header forwarded by the API gateway")
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @PathVariable UUID id,
            @org.springframework.web.bind.annotation.RequestBody Map<String, String> body,
            Authentication authentication) {

        assertAdmin(resolveUserRole(userRole, authentication));

        String status = body.get("status");
        if (status == null || (!status.equals("active") && !status.equals("inactive"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "status must be 'active' or 'inactive'");
        }

        userService.updateUserStatus(id, "active".equals(status));
        return ResponseEntity.ok(toAdminResponse(userService.findById(id)));
    }
}
