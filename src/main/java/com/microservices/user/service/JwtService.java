package com.microservices.user.service;

import com.microservices.user.entity.User;
import org.springframework.security.core.userdetails.UserDetails;

public interface JwtService {

    String generateAccessToken(User user);

    String generateRefreshToken(User user);

    /** Validates the access token signature, expiry, and revocation status. Returns a lightweight UserDetails built from claims. */
    UserDetails validateAccessToken(String token);

    String extractUserId(String token);

    String extractJti(String token);
}
