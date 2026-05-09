package com.microservices.user.controller;

import com.microservices.user.dto.*;
import com.microservices.user.service.AuthService;
import com.microservices.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    /** POST /api/auth/register — Register.tsx */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    /**
     * POST /api/auth/login — Login.tsx
     * Fully handled by JwtAuthenticationFilter; this method is never reached.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login() {
        return ResponseEntity.ok().build();
    }

    /** POST /api/auth/google — Login.tsx (Google Identity Services credential) */
    @PostMapping("/google")
    public ResponseEntity<AuthResponse> googleAuth(@Valid @RequestBody GoogleAuthRequest request) {
        return ResponseEntity.ok(authService.googleAuth(request.getCredential()));
    }

    /** POST /api/auth/forgot-password — ForgotPassword.tsx */
    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.getEmail());
        return ResponseEntity.ok(new MessageResponse("If that email is registered, a reset link has been sent."));
    }

    /** POST /api/auth/reset-password — reset-password screen (to be built in frontend) */
    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(new MessageResponse("Password updated successfully."));
    }

    /** POST /api/auth/refresh */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    /** POST /api/auth/logout — invalidates the JWT server-side */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String accessToken,
            @RequestBody(required = false) RefreshTokenRequest request) {
        authService.logout(accessToken, request != null ? request.getRefreshToken() : null);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/auth/me — rehydrates session on app init.
     * Reads the userId from the validated JWT (set as principal.username by JwtAuthorizationFilter).
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(userService.findById(UUID.fromString(principal.getUsername())));
    }
}
