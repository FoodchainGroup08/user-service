package com.microservices.user.service;

import com.microservices.user.entity.User;
import com.microservices.user.service.impl.JwtServiceImpl;
import io.jsonwebtoken.Claims;
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
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtServiceImpl Unit Tests")
class JwtServiceImplTest {

    static final String TEST_SECRET = "test-secret-key-for-jwt-testing-minimum-32-chars!!";

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @InjectMocks
    private JwtServiceImpl jwtService;

    private User testUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtService, "jwtSecret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiryMs", 900000L);
        ReflectionTestUtils.setField(jwtService, "refreshTokenExpiryMs", 604800000L);

        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("customer@example.com")
                .role(User.Role.CUSTOMER)
                .build();
    }

    // ---- generateAccessToken ----

    @Test
    @DisplayName("generateAccessToken: returns a non-blank JWT string")
    void generateAccessToken_returnsNonBlankJwt() {
        String token = jwtService.generateAccessToken(testUser);
        assertNotNull(token);
        assertFalse(token.isBlank());
        assertEquals(3, token.split("\\.").length, "JWT must have three dot-separated parts");
    }

    @Test
    @DisplayName("generateAccessToken: subject is the user's UUID")
    void generateAccessToken_subjectIsUserId() {
        String token = jwtService.generateAccessToken(testUser);
        Claims claims = parseClaims(token);
        assertEquals(testUser.getId().toString(), claims.getSubject());
    }

    @Test
    @DisplayName("generateAccessToken: includes email, role, and jti claims")
    void generateAccessToken_containsClaims() {
        String token = jwtService.generateAccessToken(testUser);
        Claims claims = parseClaims(token);

        assertEquals("customer@example.com", claims.get("email"));
        assertEquals("CUSTOMER", claims.get("role"));
        assertNotNull(claims.getId());
        assertFalse(claims.getId().isBlank());
    }

    @Test
    @DisplayName("generateAccessToken: includes branchId claim when present")
    void generateAccessToken_includesBranchId_whenPresent() {
        UUID branchId = UUID.randomUUID();
        testUser.setBranchId(branchId);

        String token = jwtService.generateAccessToken(testUser);
        Claims claims = parseClaims(token);

        assertEquals(branchId.toString(), claims.get("branchId"));
    }

    @Test
    @DisplayName("generateAccessToken: branchId claim is null when not set")
    void generateAccessToken_branchIdNull_whenNotSet() {
        testUser.setBranchId(null);
        String token = jwtService.generateAccessToken(testUser);
        Claims claims = parseClaims(token);
        assertNull(claims.get("branchId"));
    }

    @Test
    @DisplayName("generateAccessToken: token has a future expiration")
    void generateAccessToken_hasFutureExpiration() {
        String token = jwtService.generateAccessToken(testUser);
        Claims claims = parseClaims(token);
        assertTrue(claims.getExpiration().after(new Date()));
    }

    // ---- generateRefreshToken ----

    @Test
    @DisplayName("generateRefreshToken: returns a valid JWT with type=refresh")
    void generateRefreshToken_containsRefreshTypeClaim() {
        String token = jwtService.generateRefreshToken(testUser);
        assertNotNull(token);
        Claims claims = parseClaims(token);
        assertEquals(testUser.getId().toString(), claims.getSubject());
        assertEquals("refresh", claims.get("type"));
    }

    @Test
    @DisplayName("generateRefreshToken: has a longer expiry than access token")
    void generateRefreshToken_hasLongerExpiry() {
        String accessToken = jwtService.generateAccessToken(testUser);
        String refreshToken = jwtService.generateRefreshToken(testUser);

        Date accessExp = parseClaims(accessToken).getExpiration();
        Date refreshExp = parseClaims(refreshToken).getExpiration();

        assertTrue(refreshExp.after(accessExp));
    }

    // ---- validateAccessToken ----

    @Test
    @DisplayName("validateAccessToken: returns UserDetails with userId as username for a valid token")
    void validateAccessToken_returnsUserDetails_forValidToken() {
        when(tokenBlacklistService.isBlacklisted(anyString())).thenReturn(false);
        String token = jwtService.generateAccessToken(testUser);

        UserDetails details = jwtService.validateAccessToken(token);

        assertNotNull(details);
        assertEquals(testUser.getId().toString(), details.getUsername());
        assertTrue(details.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CUSTOMER")));
    }

    @Test
    @DisplayName("validateAccessToken: throws JwtException for a blacklisted token")
    void validateAccessToken_throwsJwtException_forBlacklistedToken() {
        when(tokenBlacklistService.isBlacklisted(anyString())).thenReturn(true);
        String token = jwtService.generateAccessToken(testUser);

        assertThrows(JwtException.class, () -> jwtService.validateAccessToken(token));
    }

    @Test
    @DisplayName("validateAccessToken: throws JwtException for wrong signature")
    void validateAccessToken_throwsJwtException_forWrongSignature() {
        SecretKey wrongKey = Keys.hmacShaKeyFor(
                "different-secret-key-totally-wrong-32-chars!".getBytes(StandardCharsets.UTF_8));
        String tamperedToken = Jwts.builder()
                .subject(testUser.getId().toString())
                .claim("role", "CUSTOMER")
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 900000))
                .signWith(wrongKey)
                .compact();

        assertThrows(JwtException.class, () -> jwtService.validateAccessToken(tamperedToken));
    }

    @Test
    @DisplayName("validateAccessToken: throws JwtException for an expired token")
    void validateAccessToken_throwsJwtException_forExpiredToken() {
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiryMs", -1000L);
        String expiredToken = jwtService.generateAccessToken(testUser);
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiryMs", 900000L);

        assertThrows(JwtException.class, () -> jwtService.validateAccessToken(expiredToken));
    }

    @Test
    @DisplayName("validateAccessToken: throws JwtException for a malformed token string")
    void validateAccessToken_throwsJwtException_forMalformedToken() {
        assertThrows(JwtException.class, () -> jwtService.validateAccessToken("not.a.jwt.at.all"));
    }

    @Test
    @DisplayName("validateAccessToken: throws JwtException for an empty token")
    void validateAccessToken_throwsJwtException_forEmptyToken() {
        assertThrows(JwtException.class, () -> jwtService.validateAccessToken(""));
    }

    @Test
    @DisplayName("validateAccessToken: maps display-name role claim Admin to ROLE_HEAD_OFFICE_ADMIN")
    void validateAccessToken_mapsDisplayNameAdmin_toHeadOfficeAuthority() {
        when(tokenBlacklistService.isBlacklisted(anyString())).thenReturn(false);
        UUID id = testUser.getId();
        String token = Jwts.builder()
                .subject(id.toString())
                .claim("email", "admin@example.com")
                .claim("role", "Admin")
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 900_000))
                .signWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        UserDetails details = jwtService.validateAccessToken(token);

        assertTrue(details.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_HEAD_OFFICE_ADMIN")));
    }

    @Test
    @DisplayName("validateAccessToken: throws JwtException for unknown role claim")
    void validateAccessToken_throws_forUnknownRoleClaim() {
        when(tokenBlacklistService.isBlacklisted(anyString())).thenReturn(false);
        String token = Jwts.builder()
                .subject(testUser.getId().toString())
                .claim("role", "SUPERUSER")
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 900_000))
                .signWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThrows(JwtException.class, () -> jwtService.validateAccessToken(token));
    }

    // ---- extractUserId / extractJti ----

    @Test
    @DisplayName("extractUserId: returns the user's UUID string")
    void extractUserId_returnsCorrectUserId() {
        String token = jwtService.generateAccessToken(testUser);
        assertEquals(testUser.getId().toString(), jwtService.extractUserId(token));
    }

    @Test
    @DisplayName("extractJti: returns a non-blank UUID string")
    void extractJti_returnsNonBlankJti() {
        String token = jwtService.generateAccessToken(testUser);
        String jti = jwtService.extractJti(token);
        assertNotNull(jti);
        assertFalse(jti.isBlank());
        assertDoesNotThrow(() -> UUID.fromString(jti), "jti should be a valid UUID");
    }

    @Test
    @DisplayName("extractJti: returns distinct jti for each generated token")
    void extractJti_isUniquePerToken() {
        String token1 = jwtService.generateAccessToken(testUser);
        String token2 = jwtService.generateAccessToken(testUser);
        assertNotEquals(jwtService.extractJti(token1), jwtService.extractJti(token2));
    }

    // ---- Helper ----

    private Claims parseClaims(String token) {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
