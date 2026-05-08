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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
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
@DisplayName("AuthServiceImpl Unit Tests")
class AuthServiceImplTest {

    static final String TEST_SECRET = "test-secret-key-for-jwt-testing-minimum-32-chars!!";

    @Mock private UserRepository userRepository;
    @Mock private JwtService jwtService;
    @Mock private TokenBlacklistService tokenBlacklistService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private AuthServiceImpl authService;

    private UUID testUserId;
    private User testUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "jwtSecret", TEST_SECRET);
        ReflectionTestUtils.setField(authService, "accessTokenExpiryMs", 900000L);
        ReflectionTestUtils.setField(authService, "refreshTokenExpiryMs", 604800000L);

        testUserId = UUID.randomUUID();
        testUser = User.builder()
                .id(testUserId)
                .email("user@example.com")
                .passwordHash("encoded-password")
                .role(User.Role.CUSTOMER)
                .build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ---- register ----

    @Test
    @DisplayName("register: creates and returns a UserResponse for a valid request")
    void register_createsUser_forValidRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new@example.com");
        request.setPassword("securePass1");

        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("securePass1")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        UserResponse response = authService.register(request);

        assertNotNull(response);
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("register: encodes the raw password before saving")
    void register_encodesPassword() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new@example.com");
        request.setPassword("plainTextPassword");

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode("plainTextPassword")).thenReturn("bcrypt-hash");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        authService.register(request);

        verify(passwordEncoder).encode("plainTextPassword");
        verify(userRepository).save(argThat(u -> "bcrypt-hash".equals(u.getPasswordHash())));
    }

    @Test
    @DisplayName("register: defaults role to CUSTOMER when not provided")
    void register_defaultsToCustomerRole_whenRoleNull() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new@example.com");
        request.setPassword("password1");
        request.setRole(null);

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        authService.register(request);

        verify(userRepository).save(argThat(u -> u.getRole() == User.Role.CUSTOMER));
    }

    @Test
    @DisplayName("register: uses the provided role when specified")
    void register_usesProvidedRole() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("manager@example.com");
        request.setPassword("password1");
        request.setRole(User.Role.BRANCH_MANAGER);

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        authService.register(request);

        verify(userRepository).save(argThat(u -> u.getRole() == User.Role.BRANCH_MANAGER));
    }

    @Test
    @DisplayName("register: throws IllegalArgumentException when email is already taken")
    void register_throwsException_forDuplicateEmail() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("existing@example.com");
        request.setPassword("password1");

        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any());
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
