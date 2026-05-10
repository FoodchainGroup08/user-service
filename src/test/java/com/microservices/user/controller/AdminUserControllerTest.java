package com.microservices.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservices.user.dto.UserResponse;
import com.microservices.user.entity.User;
import com.microservices.user.exception.ResourceNotFoundException;
import com.microservices.user.service.AuthService;
import com.microservices.user.service.BranchValidationService;
import com.microservices.user.service.UserService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("AdminUserController Integration Tests")
class AdminUserControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private AuthService authService;
    @MockBean private UserService userService;
    @MockBean private StringRedisTemplate stringRedisTemplate;
    @MockBean private RestTemplate restTemplate;
    @MockBean private BranchValidationService branchValidationService;

    @Value("${jwt.secret}")
    private String jwtSecret;

    /** Subject user id embedded in JWT — must match any mocked service lookups if needed */
    private UUID callerId;

    private UUID userId;
    private UserResponse customerResponse;
    private UserResponse kitchenStaffResponse;

    @BeforeEach
    void setUp() {
        callerId = UUID.randomUUID();
        userId = UUID.randomUUID();

        customerResponse = UserResponse.builder()
                .id(userId)
                .name("Alice")
                .email("alice@example.com")
                .role(User.Role.CUSTOMER)
                .isActive(true)
                .build();

        kitchenStaffResponse = UserResponse.builder()
                .id(UUID.randomUUID())
                .name("Bob")
                .email("bob@example.com")
                .role(User.Role.KITCHEN_STAFF)
                .isActive(true)
                .build();
    }

    // ---- GET /admin/users ------------------------------------------------

    @Test
    @DisplayName("GET /admin/users: returns 200 with full user list for Admin role")
    void getAllUsers_returns200_withList() throws Exception {
        when(userService.findAllUsers(null)).thenReturn(List.of(customerResponse, kitchenStaffResponse));

        mockMvc.perform(get("/api/v1/admin/users").contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, bearer("HEAD_OFFICE_ADMIN"))
                        .header("X-User-Role", "Admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].email").value("alice@example.com"))
                .andExpect(jsonPath("$[0].role").value("Customer"))
                .andExpect(jsonPath("$[0].status").value("active"));
    }

    @Test
    @DisplayName("GET /admin/users: also accepts HEAD_OFFICE_ADMIN role name")
    void getAllUsers_returns200_forHeadOfficeAdminRoleName() throws Exception {
        when(userService.findAllUsers(null)).thenReturn(List.of(customerResponse));

        mockMvc.perform(get("/api/v1/admin/users").contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, bearer("HEAD_OFFICE_ADMIN"))
                        .header("X-User-Role", "HEAD_OFFICE_ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /admin/users?role=Customer: returns 200 with filtered list")
    void getAllUsers_returns200_withRoleFilter() throws Exception {
        when(userService.findAllUsers(User.Role.CUSTOMER)).thenReturn(List.of(customerResponse));

        mockMvc.perform(get("/api/v1/admin/users").contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, bearer("HEAD_OFFICE_ADMIN"))
                        .header("X-User-Role", "Admin")
                        .param("role", "Customer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].role").value("Customer"));
    }

    @Test
    @DisplayName("GET /admin/users: returns 403 when role is not Admin")
    void getAllUsers_returns403_forNonAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users").contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, bearer("CUSTOMER"))
                        .header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /admin/users: returns 403 when X-User-Role header is missing")
    void getAllUsers_returns403_whenRoleHeaderMissing() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users").contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, bearer("CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    // ---- GET /admin/users/{id} -------------------------------------------

    @Test
    @DisplayName("GET /admin/users/{id}: returns 200 with user for valid ID")
    void getUserById_returns200_forValidId() throws Exception {
        when(userService.findById(userId)).thenReturn(customerResponse);

        mockMvc.perform(get("/api/v1/admin/users/{id}", userId).contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, bearer("HEAD_OFFICE_ADMIN"))
                        .header("X-User-Role", "Admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.role").value("Customer"));
    }

    @Test
    @DisplayName("GET /admin/users/{id}: returns 404 for unknown ID")
    void getUserById_returns404_forUnknownId() throws Exception {
        UUID unknownId = UUID.randomUUID();
        when(userService.findById(unknownId))
                .thenThrow(new ResourceNotFoundException("User not found: " + unknownId));

        mockMvc.perform(get("/api/v1/admin/users/{id}", unknownId).contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, bearer("HEAD_OFFICE_ADMIN"))
                        .header("X-User-Role", "Admin"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /admin/users/{id}: returns 403 when role is not Admin")
    void getUserById_returns403_forNonAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/{id}", userId).contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, bearer("BRANCH_MANAGER"))
                        .header("X-User-Role", "BRANCH_MANAGER"))
                .andExpect(status().isForbidden());
    }

    // ---- PATCH /admin/users/{id}/status ----------------------------------

    @Test
    @DisplayName("PATCH /admin/users/{id}/status: returns 200 with updated user when set to inactive")
    void updateStatus_returns200_withInactiveUser() throws Exception {
        UserResponse inactiveResponse = UserResponse.builder()
                .id(userId)
                .name("Alice")
                .email("alice@example.com")
                .role(User.Role.CUSTOMER)
                .isActive(false)
                .build();

        doNothing().when(userService).updateUserStatus(eq(userId), eq(false));
        when(userService.findById(userId)).thenReturn(inactiveResponse);

        mockMvc.perform(patch("/api/v1/admin/users/{id}/status", userId).contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, bearer("HEAD_OFFICE_ADMIN"))
                        .header("X-User-Role", "Admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "inactive"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("inactive"));
    }

    @Test
    @DisplayName("PATCH /admin/users/{id}/status: returns 200 with updated user when set to active")
    void updateStatus_returns200_withActiveUser() throws Exception {
        doNothing().when(userService).updateUserStatus(eq(userId), eq(true));
        when(userService.findById(userId)).thenReturn(customerResponse);

        mockMvc.perform(patch("/api/v1/admin/users/{id}/status", userId).contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, bearer("HEAD_OFFICE_ADMIN"))
                        .header("X-User-Role", "Admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "active"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("active"));
    }

    @Test
    @DisplayName("PATCH /admin/users/{id}/status: returns 403 when role is not Admin")
    void updateStatus_returns403_forNonAdmin() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/{id}/status", userId).contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, bearer("KITCHEN_STAFF"))
                        .header("X-User-Role", "KITCHEN_STAFF")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "inactive"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PATCH /admin/users/{id}/status: returns 400 for invalid status value")
    void updateStatus_returns400_forInvalidStatusValue() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/{id}/status", userId).contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, bearer("HEAD_OFFICE_ADMIN"))
                        .header("X-User-Role", "Admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "suspended"))))
                .andExpect(status().isBadRequest());
    }

    private String bearer(String roleClaim) {
        return "Bearer " + buildToken(callerId, roleClaim);
    }

    private String buildToken(UUID userId, String role) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", userId + "@example.com")
                .claim("role", role)
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 900_000))
                .signWith(key)
                .compact();
    }
}
