package com.microservices.user.service.impl;

import com.microservices.user.dto.AuthResponse;
import com.microservices.user.dto.LoginRequest;
import com.microservices.user.dto.RefreshTokenRequest;
import com.microservices.user.dto.RegisterRequest;
import com.microservices.user.dto.UserResponse;
import org.springframework.security.authentication.BadCredentialsException;
import com.microservices.user.entity.User;
import com.microservices.user.exception.ResourceNotFoundException;
import com.microservices.user.repository.UserRepository;
import com.microservices.user.service.AuthService;
import com.microservices.user.service.BranchValidationService;
import com.microservices.user.service.EmailService;
import com.microservices.user.service.JwtService;
import com.microservices.user.service.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    private final RestTemplate restTemplate;
    private final EmailService emailService;
    private final BranchValidationService branchValidationService;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-token-expiry-ms:900000}")
    private long accessTokenExpiryMs;

    @Value("${jwt.refresh-token-expiry-ms:604800000}")
    private long refreshTokenExpiryMs;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    private static final String REFRESH_PREFIX = "auth:refresh:";
    private static final String RESET_PREFIX   = "auth:reset:";
    private static final String VERIFY_PREFIX  = "auth:verify:";

    // ---- Register ----

    @Override
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered: " + request.getEmail());
        }

        if (!branchValidationService.branchExists(request.getBranchId())) {
            throw new IllegalArgumentException("Unknown or invalid branch. Pick a branch from the branch list.");
        }

        User user = User.builder()
                .email(request.getEmail())
                .name(request.getName())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(User.Role.CUSTOMER)
                .branchId(request.getBranchId())
                .emailVerified(false)
                .build();

        User saved = userRepository.save(user);

        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(
                VERIFY_PREFIX + token,
                saved.getId().toString(),
                Duration.ofHours(24)
        );
        String verifyLink = frontendUrl + "/verify-email?token=" + token;
        try {
            emailService.sendEmailVerification(saved.getEmail(), saved.getName(), verifyLink);
        } catch (Exception e) {
            log.warn("Verification email could not be queued for {}: {}", saved.getEmail(), e.getMessage());
        }

        return UserResponse.from(saved);
    }

    // ---- Login ----

    @Override
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        if (!user.isEmailVerified()) {
            throw new IllegalStateException("Please verify your email before signing in");
        }

        if (!user.isActive()) {
            throw new IllegalStateException("Your account has been deactivated. Contact support.");
        }

        return buildAuthResponse(user);
    }

    // ---- Token helpers ----

    @Override
    public AuthResponse buildAuthResponse(User user) {
        String accessToken  = jwtService.generateAccessToken(user);
        String refreshToken = createRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(accessTokenExpiryMs / 1000)
                .user(UserResponse.from(user))
                .build();
    }

    @Override
    public String createRefreshToken(User user) {
        String token = jwtService.generateRefreshToken(user);
        redisTemplate.opsForValue().set(
                REFRESH_PREFIX + token,
                user.getId().toString(),
                Duration.ofMillis(refreshTokenExpiryMs)
        );
        return token;
    }

    // ---- Refresh ----

    @Override
    public AuthResponse refresh(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        String userId = redisTemplate.opsForValue().get(REFRESH_PREFIX + refreshToken);
        if (userId == null) {
            throw new JwtException("Invalid or expired refresh token");
        }

        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            Jwts.parser().verifyWith(key).build().parseSignedClaims(refreshToken);
        } catch (JwtException e) {
            redisTemplate.delete(REFRESH_PREFIX + refreshToken);
            throw new JwtException("Invalid refresh token");
        }

        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!user.isEmailVerified()) {
            throw new IllegalStateException("Verify your email before signing in.");
        }

        redisTemplate.delete(REFRESH_PREFIX + refreshToken);
        String newAccessToken  = jwtService.generateAccessToken(user);
        String newRefreshToken = createRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(accessTokenExpiryMs / 1000)
                .user(UserResponse.from(user))
                .build();
    }

    // ---- Logout ----

    @Override
    public void logout(String accessToken, String refreshToken) {
        if (accessToken != null && accessToken.startsWith("Bearer ")) {
            String token = accessToken.substring(7);
            try {
                String jti = jwtService.extractJti(token);
                tokenBlacklistService.blacklist(jti, accessTokenExpiryMs / 1000);
            } catch (JwtException ignored) {
                // Already invalid — nothing to blacklist
            }
        }
        if (refreshToken != null && !refreshToken.isBlank()) {
            redisTemplate.delete(REFRESH_PREFIX + refreshToken);
        }
    }

    // ---- Forgot / Reset password ----

    @Override
    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("No account found with this email"));
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(RESET_PREFIX + token, user.getId().toString(), Duration.ofHours(1));
        String resetLink = frontendUrl + "/reset-password?token=" + token;
        try {
            emailService.sendPasswordReset(email, user.getName(), resetLink);
        } catch (Exception e) {
            log.warn("Password reset email could not be queued for {}: {}", email, e.getMessage());
        }
    }

    @Override
    public void resetPassword(String token, String newPassword) {
        String userId = redisTemplate.opsForValue().get(RESET_PREFIX + token);
        if (userId == null) {
            throw new IllegalArgumentException("Invalid or expired reset token");
        }

        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        redisTemplate.delete(RESET_PREFIX + token);
    }

    @Override
    public void verifyEmail(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Verification token is required");
        }
        String userId = redisTemplate.opsForValue().get(VERIFY_PREFIX + token);
        if (userId == null) {
            throw new IllegalArgumentException("Invalid or expired verification link");
        }

        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.isEmailVerified()) {
            throw new IllegalStateException("Email is already verified");
        }
        user.setEmailVerified(true);
        userRepository.save(user);
        redisTemplate.delete(VERIFY_PREFIX + token);
    }

    @Override
    public void resendVerification(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("No account found with this email"));
        if (user.isEmailVerified()) {
            throw new IllegalStateException("Email is already verified");
        }
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(VERIFY_PREFIX + token, user.getId().toString(), Duration.ofHours(24));
        String verifyLink = frontendUrl + "/verify-email?token=" + token;
        try {
            emailService.sendEmailVerification(user.getEmail(), user.getName(), verifyLink);
        } catch (Exception e) {
            log.warn("Verification email could not be queued for {}: {}", email, e.getMessage());
        }
    }

    // ---- Google OAuth ----

    @Override
    @SuppressWarnings("unchecked")
    public AuthResponse googleAuth(String idToken) {
        Map<String, String> googleInfo;
        try {
            String url = "https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken;
            googleInfo = restTemplate.getForObject(url, Map.class);
        } catch (HttpClientErrorException e) {
            throw new IllegalArgumentException("Invalid or expired Google token");
        }

        if (googleInfo == null || !"true".equals(googleInfo.get("email_verified"))) {
            throw new IllegalArgumentException("Google token verification failed");
        }

        String googleSub = googleInfo.get("sub");   // stable Google user ID
        String email     = googleInfo.get("email");
        String name      = googleInfo.get("name");

        User user = userRepository
                .findByOauth2ProviderAndOauth2ProviderId("google", googleSub)
                .orElseGet(() -> userRepository.findByEmail(email)
                        .map(existing -> {
                            // Link existing account to Google
                            existing.setOauth2Provider("google");
                            existing.setOauth2ProviderId(googleSub);
                            existing.setEmailVerified(true);
                            if (existing.getName() == null) existing.setName(name);
                            return userRepository.save(existing);
                        })
                        .orElseGet(() -> userRepository.save(
                                User.builder()
                                        .email(email)
                                        .name(name)
                                        .role(User.Role.CUSTOMER)
                                        .emailVerified(true)
                                        .oauth2Provider("google")
                                        .oauth2ProviderId(googleSub)
                                        .build()
                        ))
                );

        return buildAuthResponse(user);
    }
}
