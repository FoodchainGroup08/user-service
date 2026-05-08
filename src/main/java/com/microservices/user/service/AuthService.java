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

    /** Stores a new refresh token in Redis. Returns the token string. */
    String createRefreshToken(User user);
}
