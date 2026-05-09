package com.microservices.user.service;

import com.microservices.user.dto.AuthResponse;
import com.microservices.user.dto.RefreshTokenRequest;
import com.microservices.user.dto.RegisterRequest;
import com.microservices.user.dto.UserResponse;
import com.microservices.user.entity.User;

public interface AuthService {

    UserResponse register(RegisterRequest request);

    /** Called by JwtAuthenticationFilter after successful credential validation. */
    AuthResponse buildAuthResponse(User user);

    AuthResponse refresh(RefreshTokenRequest request);

    void logout(String accessToken, String refreshToken);

    /** Stores a new refresh token in Redis and returns the token string. */
    String createRefreshToken(User user);

    /** Generates a one-time reset token (stored in Redis, 1-hour TTL) and logs the reset link. */
    void forgotPassword(String email);

    /** Validates the reset token, encodes and saves the new password, then invalidates the token. */
    void resetPassword(String token, String newPassword);

    /** Validates a Google ID token, then finds or creates a local user and returns a JWT pair. */
    AuthResponse googleAuth(String idToken);
}
