package com.microservices.user.service;

import com.microservices.user.dto.AuthResponse;
import com.microservices.user.dto.RefreshTokenRequest;
import com.microservices.user.dto.RegisterRequest;
import com.microservices.user.dto.UserResponse;
import com.microservices.user.entity.User;
import com.microservices.user.exception.ResourceNotFoundException;
import com.microservices.user.repository.UserRepository;
import com.microservices.user.service.impl.AuthServiceImpl;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuthServiceImpl Unit Tests")
class AuthServiceImplTest {

    static final String TEST_SECRET = "test-secret-key-for-jwt-testing-minimum-32-chars!!";

    @Mock private UserRepository userRepository;
    @Mock private JwtService jwtService;
    @Mock private TokenBlacklistService tokenBlacklistService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private RestTemplate restTemplate;
    @Mock private EmailService emailService;
    @Mock private BranchValidationService branchValidationService;

    @InjectMocks
    private AuthServiceImpl authService;

    private static final UUID VALID_BRANCH_ID = UUID.fromString("a0000001-0000-4000-8000-000000000001");

    private UUID testUserId;
    private User testUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "jwtSecret", TEST_SECRET);
        ReflectionTestUtils.setField(authService, "accessTokenExpiryMs", 900000L);
        ReflectionTestUtils.setField(authService, "refreshTokenExpiryMs", 604800000L);
        ReflectionTestUtils.setField(authService, "frontendUrl", "http://localhost:5173");

        testUserId = UUID.randomUUID();
        testUser = User.builder()
                .id(testUserId)
                .email("user@example.com")
                .passwordHash("encoded-password")
                .role(User.Role.CUSTOMER)
                .emailVerified(true)
                .build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(branchValidationService.branchExists(any(UUID.class))).thenReturn(true);
    }

    // ---- register ----

    @Test
    @DisplayName("register: creates and returns a UserResponse for a valid request")
    void register_createsUser_forValidRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new@example.com");
        request.setPassword("Secure@123!");
        request.setBranchId(VALID_BRANCH_ID);

        User savedStub = User.builder()
                .id(testUserId)
                .email("new@example.com")
                .passwordHash("hashed-password")
                .role(User.Role.CUSTOMER)
                .branchId(VALID_BRANCH_ID)
                .emailVerified(false)
                .build();

        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Secure@123!")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenReturn(savedStub);

        UserResponse response = authService.register(request);

        assertNotNull(response);
        verify(userRepository).save(any(User.class));
        verify(emailService).sendEmailVerification(anyString(), any(), argThat(link -> link.contains("/verify-email?token=")));
        verify(valueOperations).set(startsWith("auth:verify:"), eq(savedStub.getId().toString()), eq(Duration.ofHours(24)));
    }

    @Test
    @DisplayName("register: encodes the raw password before saving")
    void register_encodesPassword() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new@example.com");
        request.setPassword("PlainText@1!");
        request.setBranchId(VALID_BRANCH_ID);

        User savedStub = User.builder()
                .id(testUserId)
                .email("new@example.com")
                .role(User.Role.CUSTOMER)
                .branchId(VALID_BRANCH_ID)
                .emailVerified(false)
                .build();

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode("PlainText@1!")).thenReturn("bcrypt-hash");
        when(userRepository.save(any(User.class))).thenReturn(savedStub);

        authService.register(request);

        verify(passwordEncoder).encode("PlainText@1!");
        verify(userRepository).save(argThat(u -> "bcrypt-hash".equals(u.getPasswordHash())));
        verify(emailService).sendEmailVerification(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("register: always assigns CUSTOMER role")
    void register_alwaysCustomerRole() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new@example.com");
        request.setPassword("Secure@123!");
        request.setBranchId(VALID_BRANCH_ID);

        User savedStub = User.builder()
                .id(testUserId)
                .email("new@example.com")
                .role(User.Role.CUSTOMER)
                .branchId(VALID_BRANCH_ID)
                .emailVerified(false)
                .build();

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenReturn(savedStub);

        authService.register(request);

        verify(userRepository).save(argThat(u -> u.getRole() == User.Role.CUSTOMER));
    }

    @Test
    @DisplayName("register: rejects unknown branch id")
    void register_throws_forUnknownBranch() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new@example.com");
        request.setPassword("Secure@123!");
        request.setBranchId(UUID.randomUUID());

        when(branchValidationService.branchExists(request.getBranchId())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any());
        verify(emailService, never()).sendEmailVerification(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("register: throws IllegalArgumentException when email is already taken")
    void register_throwsException_forDuplicateEmail() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("existing@example.com");
        request.setPassword("Secure@123!");
        request.setBranchId(VALID_BRANCH_ID);

        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any());
        verify(emailService, never()).sendEmailVerification(anyString(), any(), anyString());
    }

    // ---- buildAuthResponse ----

    @Test
    @DisplayName("buildAuthResponse: returns AuthResponse with access and refresh tokens")
    void buildAuthResponse_returnsAuthResponseWithBothTokens() {
        when(jwtService.generateAccessToken(testUser)).thenReturn("access-token-xyz");
        when(jwtService.generateRefreshToken(testUser)).thenReturn("refresh-token-jwt");

        AuthResponse response = authService.buildAuthResponse(testUser);

        assertNotNull(response);
        assertEquals("access-token-xyz", response.getAccessToken());
        assertNotNull(response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());
        assertNotNull(response.getUser());
        assertEquals(testUser.getEmail(), response.getUser().getEmail());
    }

    @Test
    @DisplayName("buildAuthResponse: expiresIn matches access token expiry in seconds")
    void buildAuthResponse_expiresInMatchesAccessTokenExpiry() {
        when(jwtService.generateAccessToken(testUser)).thenReturn("access-token");
        when(jwtService.generateRefreshToken(testUser)).thenReturn("refresh-jwt");

        AuthResponse response = authService.buildAuthResponse(testUser);

        assertEquals(900L, response.getExpiresIn()); // 900000ms / 1000
    }

    // ---- createRefreshToken ----

    @Test
    @DisplayName("createRefreshToken: stores the token in Redis with 7-day TTL")
    void createRefreshToken_storesInRedis() {
        when(jwtService.generateRefreshToken(testUser)).thenReturn("refresh-jwt-token");

        String token = authService.createRefreshToken(testUser);

        assertNotNull(token);
        verify(valueOperations).set(
                eq("auth:refresh:" + token),
                eq(testUserId.toString()),
                eq(Duration.ofMillis(604800000L))
        );
    }

    // ---- forgot password ----

    @Test
    @DisplayName("forgotPassword: stores token and queues reset email when user exists")
    void forgotPassword_sendsEmail_whenUserExists() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));

        authService.forgotPassword("user@example.com");

        verify(valueOperations).set(
                startsWith("auth:reset:"),
                eq(testUserId.toString()),
                eq(Duration.ofHours(1))
        );
        verify(emailService).sendPasswordReset(
                eq("user@example.com"),
                any(),
                argThat(link -> link.startsWith("http://localhost:5173/reset-password?token=")));
    }

    @Test
    @DisplayName("forgotPassword: does nothing when email is unknown")
    void forgotPassword_noEmail_whenUnknown() {
        when(userRepository.findByEmail("noone@example.com")).thenReturn(Optional.empty());

        authService.forgotPassword("noone@example.com");

        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
        verify(emailService, never()).sendPasswordReset(anyString(), any(), anyString());
    }

    // ---- refresh ----

    @Test
    @DisplayName("refresh: returns new tokens for a valid refresh token")
    void refresh_returnsNewTokens_forValidToken() {
        String refreshToken = buildValidRefreshToken(testUserId);
        when(valueOperations.get("auth:refresh:" + refreshToken)).thenReturn(testUserId.toString());
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(jwtService.generateAccessToken(testUser)).thenReturn("new-access-token");
        when(jwtService.generateRefreshToken(testUser)).thenReturn("new-refresh-jwt");

        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(refreshToken);

        AuthResponse response = authService.refresh(request);

        assertEquals("new-access-token", response.getAccessToken());
        assertNotNull(response.getRefreshToken());
    }

    @Test
    @DisplayName("refresh: rotates the refresh token (deletes old, stores new)")
    void refresh_rotatesRefreshToken() {
        String oldToken = buildValidRefreshToken(testUserId);
        when(valueOperations.get("auth:refresh:" + oldToken)).thenReturn(testUserId.toString());
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(jwtService.generateAccessToken(testUser)).thenReturn("new-access");
        when(jwtService.generateRefreshToken(testUser)).thenReturn("new-refresh-jwt");

        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(oldToken);

        authService.refresh(request);

        verify(redisTemplate).delete("auth:refresh:" + oldToken);
        verify(valueOperations).set(eq("auth:refresh:" + "new-refresh-jwt"), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("refresh: throws JwtException when token is not in Redis")
    void refresh_throwsJwtException_whenTokenNotInRedis() {
        String token = buildValidRefreshToken(testUserId);
        when(valueOperations.get(anyString())).thenReturn(null);

        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(token);

        assertThrows(JwtException.class, () -> authService.refresh(request));
    }

    @Test
    @DisplayName("refresh: throws JwtException for a tampered (wrong signature) token")
    void refresh_throwsJwtException_forTamperedToken() {
        String tamperedToken = buildTokenWithWrongKey(testUserId);
        when(valueOperations.get(anyString())).thenReturn(testUserId.toString());

        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(tamperedToken);

        assertThrows(JwtException.class, () -> authService.refresh(request));
    }

    @Test
    @DisplayName("refresh: throws ResourceNotFoundException when user no longer exists")
    void refresh_throwsResourceNotFoundException_whenUserDeleted() {
        String token = buildValidRefreshToken(testUserId);
        when(valueOperations.get("auth:refresh:" + token)).thenReturn(testUserId.toString());
        when(userRepository.findById(testUserId)).thenReturn(Optional.empty());

        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(token);

        assertThrows(ResourceNotFoundException.class, () -> authService.refresh(request));
    }

    @Test
    @DisplayName("refresh: throws IllegalStateException when email is not verified")
    void refresh_throwsIllegalState_whenEmailNotVerified() {
        User unverified = User.builder()
                .id(testUserId)
                .email("user@example.com")
                .role(User.Role.CUSTOMER)
                .emailVerified(false)
                .build();

        String token = buildValidRefreshToken(testUserId);
        when(valueOperations.get("auth:refresh:" + token)).thenReturn(testUserId.toString());
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(unverified));

        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(token);

        assertThrows(IllegalStateException.class, () -> authService.refresh(request));
    }

    // ---- logout ----

    @Test
    @DisplayName("logout: blacklists the jti from the access token")
    void logout_blacklistsAccessTokenJti() {
        String jti = UUID.randomUUID().toString();
        String accessToken = "Bearer " + buildValidAccessToken(testUserId, jti);
        when(jwtService.extractJti(anyString())).thenReturn(jti);

        authService.logout(accessToken, null);

        verify(tokenBlacklistService).blacklist(eq(jti), anyLong());
    }

    @Test
    @DisplayName("logout: deletes the refresh token from Redis when provided")
    void logout_deletesRefreshToken_whenProvided() {
        String refreshToken = "some-refresh-token";
        when(jwtService.extractJti(anyString())).thenReturn("jti-123");

        authService.logout("Bearer some-access-token", refreshToken);

        verify(redisTemplate).delete("auth:refresh:" + refreshToken);
    }

    @Test
    @DisplayName("logout: gracefully handles null access token")
    void logout_handlesNullAccessToken() {
        assertDoesNotThrow(() -> authService.logout(null, "some-refresh-token"));
        verify(tokenBlacklistService, never()).blacklist(anyString(), anyLong());
    }

    @Test
    @DisplayName("logout: gracefully handles null refresh token")
    void logout_handlesNullRefreshToken() {
        when(jwtService.extractJti(anyString())).thenReturn("jti-xyz");
        assertDoesNotThrow(() -> authService.logout("Bearer valid-token", null));
        verify(redisTemplate, never()).delete(anyString());
    }

    // ---- Helpers ----

    private String buildValidRefreshToken(UUID userId) {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(userId.toString())
                .claim("type", "refresh")
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 604800000L))
                .signWith(key)
                .compact();
    }

    private String buildValidAccessToken(UUID userId, String jti) {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", "CUSTOMER")
                .id(jti)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 900000L))
                .signWith(key)
                .compact();
    }

    private String buildTokenWithWrongKey(UUID userId) {
        SecretKey wrongKey = Keys.hmacShaKeyFor(
                "totally-wrong-key-that-does-not-match-secret!!".getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 604800000L))
                .signWith(wrongKey)
                .compact();
    }
}
