package com.microservices.user.service.impl;

import com.microservices.user.entity.User;
import com.microservices.user.service.JwtService;
import com.microservices.user.service.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtServiceImpl implements JwtService {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-token-expiry-ms:900000}")
    private long accessTokenExpiryMs;

    @Value("${jwt.refresh-token-expiry-ms:604800000}")
    private long refreshTokenExpiryMs;

    private final TokenBlacklistService tokenBlacklistService;

    @Override
    public String generateAccessToken(User user) {
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .claim("branchId", user.getBranchId() != null ? user.getBranchId().toString() : null)
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiryMs))
                .signWith(getKey())
                .compact();
    }

    @Override
    public String generateRefreshToken(User user) {
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("type", "refresh")
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiryMs))
                .signWith(getKey())
                .compact();
    }

    @Override
    public UserDetails validateAccessToken(String token) {
        Claims claims = parseClaims(token);

        String jti = claims.getId();
        if (tokenBlacklistService.isBlacklisted(jti)) {
            throw new JwtException("Token has been revoked");
        }

        String role = claims.get("role", String.class);
        if (role == null || role.isBlank()) {
            throw new JwtException("Missing or empty role claim");
        }
        // Map display names ("Admin") and enum names (HEAD_OFFICE_ADMIN) to a single Spring authority
        String authorityRole;
        try {
            authorityRole = User.Role.fromDisplayName(role.trim()).name();
        } catch (IllegalArgumentException e) {
            throw new JwtException("Unknown role in token: " + role);
        }

        return org.springframework.security.core.userdetails.User.builder()
                .username(claims.getSubject())
                .password("")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + authorityRole)))
                .build();
    }

    @Override
    public String extractUserId(String token) {
        return parseClaims(token).getSubject();
    }

    @Override
    public String extractJti(String token) {
        return parseClaims(token).getId();
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (IllegalArgumentException e) {
            throw new JwtException("Token must not be null or empty", e);
        }
    }

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }
}
