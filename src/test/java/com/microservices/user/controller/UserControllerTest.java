package com.microservices.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservices.user.dto.UserResponse;
import com.microservices.user.entity.User;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("UserController Integration Tests")
class UserControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private UserService userService;
    @MockBean private AuthService authService;
    @MockBean private StringRedisTemplate stringRedisTemplate;
    @MockBean private org.springframework.web.client.RestTemplate restTemplate;
    @MockBean private BranchValidationService branchValidationService;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private UUID customerId;
    private UUID adminId;
    private UserResponse customerResponse;
    private UserResponse adminResponse;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        adminId = UUID.randomUUID();

        customerResponse = UserResponse.builder()
                .id(customerId)
                .email("customer@example.com")
                .role(User.Role.CUSTOMER)
                .isActive(true)
                .build();

        adminResponse = UserResponse.builder()
                .id(adminId)
                .email("admin@example.com")
                .role(User.Role.HEAD_OFFICE_ADMIN)
                .isActive(true)
                .build();
    }

    // ---- GET /users/me ----

    @Test
    @DisplayName("GET /me: returns 401 when no Authorization header is present")
    void getMe_returns401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").contextPath("/api"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /me: returns 200 with the current user's profile for a valid JWT")
    void getMe_returns200_withUserProfile_forValidJwt() throws Exception {
        when(userService.findById(customerId)).thenReturn(customerResponse);

        mockMvc.perform(get("/api/v1/users/me").contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buildToken(customerId, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("customer@example.com"))
                .andExpect(jsonPath("$.role").value("Customer"));
    }

    @Test
    @DisplayName("GET /me: returns 401 for a tampered (invalid signature) token")
    void getMe_returns401_forTamperedToken() throws Exception {
        String tamperedToken = buildTokenWithWrongKey(customerId, "CUSTOMER");

        mockMvc.perform(get("/api/v1/users/me").contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tamperedToken))
                .andExpect(status().isUnauthorized());
    }

    // ---- GET /api/v1/users ----

    @Test
    @DisplayName("GET /users: returns 401 when unauthenticated")
    void getAllUsers_returns401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/users").contextPath("/api"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /users: returns 403 Forbidden for a CUSTOMER role")
    void getAllUsers_returns403_forCustomer() throws Exception {
        mockMvc.perform(get("/api/v1/users").contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buildToken(customerId, "CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /users: returns 200 OK with user list for HEAD_OFFICE_ADMIN")
    void getAllUsers_returns200_forAdmin() throws Exception {
        when(userService.findAll()).thenReturn(List.of(customerResponse, adminResponse));

        mockMvc.perform(get("/api/v1/users").contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buildToken(adminId, "HEAD_OFFICE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("GET /users: returns 403 Forbidden for KITCHEN_STAFF role")
    void getAllUsers_returns403_forKitchenStaff() throws Exception {
        UUID staffId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/users").contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buildToken(staffId, "KITCHEN_STAFF")))
                .andExpect(status().isForbidden());
    }

    // ---- GET /users/{id} ----

    @Test
    @DisplayName("GET /users/{id}: returns 200 OK for HEAD_OFFICE_ADMIN")
    void getUserById_returns200_forAdmin() throws Exception {
        UUID targetId = UUID.randomUUID();
        when(userService.findById(targetId)).thenReturn(customerResponse);

        mockMvc.perform(get("/api/v1/users/" + targetId).contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buildToken(adminId, "HEAD_OFFICE_ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /users/{id}: returns 200 OK for BRANCH_MANAGER")
    void getUserById_returns200_forBranchManager() throws Exception {
        UUID managerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        when(userService.findById(targetId)).thenReturn(customerResponse);

        mockMvc.perform(get("/api/v1/users/" + targetId).contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buildToken(managerId, "BRANCH_MANAGER")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /users/{id}: returns 403 Forbidden for CUSTOMER role")
    void getUserById_returns403_forCustomer() throws Exception {
        UUID targetId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/users/" + targetId).contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buildToken(customerId, "CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    // ---- Helpers ----

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

    private String buildTokenWithWrongKey(UUID userId, String role) {
        SecretKey wrongKey = Keys.hmacShaKeyFor(
                "totally-wrong-key-32-chars-at-least-012345".getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", role)
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 900_000))
                .signWith(wrongKey)
                .compact();
    }
}
