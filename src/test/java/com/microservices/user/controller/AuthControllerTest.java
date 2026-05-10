package com.microservices.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservices.user.dto.AuthResponse;
import com.microservices.user.dto.ForgotPasswordRequest;
import com.microservices.user.dto.RefreshTokenRequest;
import com.microservices.user.dto.RegisterRequest;
import com.microservices.user.dto.ResetPasswordRequest;
import com.microservices.user.dto.ResendVerificationRequest;
import com.microservices.user.dto.UserResponse;
import com.microservices.user.exception.ResourceNotFoundException;
import com.microservices.user.entity.User;
import com.microservices.user.service.AuthService;
import com.microservices.user.service.BranchValidationService;
import com.microservices.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("AuthController Integration Tests")
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private AuthService authService;
    @MockBean private UserService userService;
    @MockBean private StringRedisTemplate stringRedisTemplate;
    @MockBean private RestTemplate restTemplate;
    @MockBean private BranchValidationService branchValidationService;

    private static final UUID SAMPLE_BRANCH_ID = UUID.fromString("a0000001-0000-4000-8000-000000000001");

    private UserResponse sampleUserResponse;

    @BeforeEach
    void setUp() {
        sampleUserResponse = UserResponse.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .role(User.Role.CUSTOMER)
                .isActive(true)
                .emailVerified(false)
                .build();
    }

    // ---- POST /api/v1/auth/register ----

    @Test
    @DisplayName("register: returns 201 Created with UserResponse for a valid payload")
    void register_returns201_forValidRequest() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new@example.com");
        request.setPassword("Secure@123!");
        request.setBranchId(SAMPLE_BRANCH_ID);

        when(authService.register(any(RegisterRequest.class))).thenReturn(sampleUserResponse);

        mockMvc.perform(post("/api/v1/auth/register").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.role").value("Customer"));
    }

    @Test
    @DisplayName("register: returns 400 Bad Request for an invalid email")
    void register_returns400_forInvalidEmail() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("not-an-email");
        request.setPassword("Secure@123!");
        request.setBranchId(SAMPLE_BRANCH_ID);

        mockMvc.perform(post("/api/v1/auth/register").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("register: returns 400 Bad Request when email is blank")
    void register_returns400_forBlankEmail() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("");
        request.setPassword("Secure@123!");
        request.setBranchId(SAMPLE_BRANCH_ID);

        mockMvc.perform(post("/api/v1/auth/register").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("register: returns 400 Bad Request when password is too short (< 8 chars)")
    void register_returns400_forShortPassword() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("user@example.com");
        request.setPassword("short");
        request.setBranchId(SAMPLE_BRANCH_ID);

        mockMvc.perform(post("/api/v1/auth/register").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("register: returns 400 Bad Request when password has no uppercase letter")
    void register_returns400_forPasswordMissingUppercase() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("user@example.com");
        request.setPassword("secure@123!");
        request.setBranchId(SAMPLE_BRANCH_ID);

        mockMvc.perform(post("/api/v1/auth/register").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("register: returns 400 Bad Request when password has no lowercase letter")
    void register_returns400_forPasswordMissingLowercase() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("user@example.com");
        request.setPassword("SECURE@123!");
        request.setBranchId(SAMPLE_BRANCH_ID);

        mockMvc.perform(post("/api/v1/auth/register").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("register: returns 400 Bad Request when password has no digit")
    void register_returns400_forPasswordMissingDigit() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("user@example.com");
        request.setPassword("Secure@Pass!");
        request.setBranchId(SAMPLE_BRANCH_ID);

        mockMvc.perform(post("/api/v1/auth/register").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("register: returns 400 Bad Request when password has no special character")
    void register_returns400_forPasswordMissingSpecialChar() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("user@example.com");
        request.setPassword("SecurePass1");
        request.setBranchId(SAMPLE_BRANCH_ID);

        mockMvc.perform(post("/api/v1/auth/register").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("register: returns 400 Bad Request when email is already taken")
    void register_returns400_forDuplicateEmail() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("taken@example.com");
        request.setPassword("Secure@123!");
        request.setBranchId(SAMPLE_BRANCH_ID);

        when(authService.register(any())).thenThrow(new IllegalArgumentException("Email already registered"));

        mockMvc.perform(post("/api/v1/auth/register").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ---- POST /auth/forgot-password ----

    @Test
    @DisplayName("forgotPassword: returns 200 OK when email exists")
    void forgotPassword_returns200_whenEmailExists() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("user@example.com");

        doNothing().when(authService).forgotPassword("user@example.com");

        mockMvc.perform(post("/api/v1/auth/forgot-password").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("forgotPassword: returns 404 Not Found when email is not registered")
    void forgotPassword_returns404_whenEmailNotRegistered() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("ghost@example.com");

        doThrow(new ResourceNotFoundException("No account found with this email"))
                .when(authService).forgotPassword("ghost@example.com");

        mockMvc.perform(post("/api/v1/auth/forgot-password").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No account found with this email"));
    }

    @Test
    @DisplayName("forgotPassword: returns 400 Bad Request for invalid email format")
    void forgotPassword_returns400_forInvalidEmailFormat() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("not-an-email");

        mockMvc.perform(post("/api/v1/auth/forgot-password").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("forgotPassword: returns 400 Bad Request when email is blank")
    void forgotPassword_returns400_forBlankEmail() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("");

        mockMvc.perform(post("/api/v1/auth/forgot-password").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ---- POST /auth/reset-password ----

    @Test
    @DisplayName("resetPassword: returns 200 OK for a valid token and new password")
    void resetPassword_returns200_forValidRequest() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("valid-token");
        request.setNewPassword("NewPass@1!");

        doNothing().when(authService).resetPassword("valid-token", "NewPass@1!");

        mockMvc.perform(post("/api/v1/auth/reset-password").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("resetPassword: returns 400 Bad Request for invalid or expired token")
    void resetPassword_returns400_forInvalidToken() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("expired-token");
        request.setNewPassword("NewPass@1!");

        doThrow(new IllegalArgumentException("Invalid or expired reset token"))
                .when(authService).resetPassword("expired-token", "NewPass@1!");

        mockMvc.perform(post("/api/v1/auth/reset-password").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or expired reset token"));
    }

    @Test
    @DisplayName("resetPassword: returns 400 Bad Request when token is blank")
    void resetPassword_returns400_forBlankToken() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("");
        request.setNewPassword("NewPass@1!");

        mockMvc.perform(post("/api/v1/auth/reset-password").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("resetPassword: returns 400 Bad Request when new password is too weak")
    void resetPassword_returns400_forWeakPassword() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("valid-token");
        request.setNewPassword("weak");

        mockMvc.perform(post("/api/v1/auth/reset-password").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ---- GET /auth/verify-email ----

    @Test
    @DisplayName("verifyEmail: returns 200 OK for a valid token")
    void verifyEmail_returns200_forValidToken() throws Exception {
        doNothing().when(authService).verifyEmail("valid-token");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/auth/verify-email").contextPath("/api")
                        .param("token", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("verifyEmail: returns 400 Bad Request for an invalid or expired token")
    void verifyEmail_returns400_forExpiredToken() throws Exception {
        doThrow(new IllegalArgumentException("Invalid or expired verification link"))
                .when(authService).verifyEmail("expired-token");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/auth/verify-email").contextPath("/api")
                        .param("token", "expired-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or expired verification link"));
    }

    @Test
    @DisplayName("verifyEmail: returns 403 Forbidden when email is already verified")
    void verifyEmail_returns403_whenAlreadyVerified() throws Exception {
        doThrow(new IllegalStateException("Email is already verified"))
                .when(authService).verifyEmail("used-token");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/auth/verify-email").contextPath("/api")
                        .param("token", "used-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Email is already verified"));
    }

    // ---- POST /auth/resend-verification ----

    @Test
    @DisplayName("resendVerification: returns 200 OK for an unverified account")
    void resendVerification_returns200_forUnverifiedAccount() throws Exception {
        ResendVerificationRequest request = new ResendVerificationRequest();
        request.setEmail("user@example.com");

        doNothing().when(authService).resendVerification("user@example.com");

        mockMvc.perform(post("/api/v1/auth/resend-verification").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("resendVerification: returns 404 Not Found when email is not registered")
    void resendVerification_returns404_whenEmailNotFound() throws Exception {
        ResendVerificationRequest request = new ResendVerificationRequest();
        request.setEmail("ghost@example.com");

        doThrow(new ResourceNotFoundException("No account found with this email"))
                .when(authService).resendVerification("ghost@example.com");

        mockMvc.perform(post("/api/v1/auth/resend-verification").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No account found with this email"));
    }

    @Test
    @DisplayName("resendVerification: returns 403 Forbidden when email is already verified")
    void resendVerification_returns403_whenAlreadyVerified() throws Exception {
        ResendVerificationRequest request = new ResendVerificationRequest();
        request.setEmail("verified@example.com");

        doThrow(new IllegalStateException("Email is already verified"))
                .when(authService).resendVerification("verified@example.com");

        mockMvc.perform(post("/api/v1/auth/resend-verification").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Email is already verified"));
    }

    @Test
    @DisplayName("resendVerification: returns 400 Bad Request for invalid email format")
    void resendVerification_returns400_forInvalidEmail() throws Exception {
        ResendVerificationRequest request = new ResendVerificationRequest();
        request.setEmail("not-an-email");

        mockMvc.perform(post("/api/v1/auth/resend-verification").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ---- POST /auth/refresh ----

    @Test
    @DisplayName("refresh: returns 200 OK with new tokens for a valid refresh token")
    void refresh_returns200_forValidRefreshToken() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("valid-refresh-token");

        AuthResponse authResponse = AuthResponse.builder()
                .accessToken("new-access-token")
                .refreshToken("new-refresh-token")
                .tokenType("Bearer")
                .expiresIn(900L)
                .user(sampleUserResponse)
                .build();
        when(authService.refresh(any(RefreshTokenRequest.class))).thenReturn(authResponse);

        mockMvc.perform(post("/api/v1/auth/refresh").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("refresh: returns 400 Bad Request when refresh token is blank")
    void refresh_returns400_forBlankRefreshToken() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("");

        mockMvc.perform(post("/api/v1/auth/refresh").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ---- POST /auth/logout ----

    @Test
    @DisplayName("logout: returns 204 No Content")
    void logout_returns204() throws Exception {
        doNothing().when(authService).logout(any(), any());

        mockMvc.perform(post("/api/v1/auth/logout").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("logout: accepts optional refresh token body")
    void logout_accepts_optionalRefreshTokenBody() throws Exception {
        doNothing().when(authService).logout(any(), any());

        RefreshTokenRequest body = new RefreshTokenRequest();
        body.setRefreshToken("some-refresh-token");

        mockMvc.perform(post("/api/v1/auth/logout").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNoContent());
    }
}
