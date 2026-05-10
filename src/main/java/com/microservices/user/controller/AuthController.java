package com.microservices.user.controller;

import com.microservices.user.dto.*;
import com.microservices.user.service.AuthService;
import com.microservices.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
@Tag(
        name = "Auth",
        description = """
                Registration, login, refresh, password reset, email verification.

                **Calling from API Gateway Swagger (`http://localhost:8080`)** requires CORS: user-service allows those origins by default.
                If you still see **403** with body `Invalid CORS request`, add your browser origin to `APP_CORS_ALLOWED_ORIGINS` / `app.cors.allowed-origins`.

                **Login** may return **403** if the account exists but **email is not verified** (verify via email link first).

                Base path via gateway: `/api/v1/auth`. User-service uses servlet context `/api`, so direct port URLs look like `http://localhost:8086/api/v1/auth/...`.""")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @Operation(summary = "Register a new user")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "User created; verify email before login if required by policy."),
        @ApiResponse(responseCode = "400", description = "Validation error, duplicate email, or unknown branchId"),
        @ApiResponse(
                responseCode = "403",
                description = "CORS rejected (unknown Origin), or Spring Security denied — see Auth tag description")
    })
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @org.springframework.web.bind.annotation.RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @Operation(
        summary = "Login with email and password",
        description = "Intercepted by JwtAuthenticationFilter before reaching this method. Send JSON with `email` and `password`."
    )
    @RequestBody(
        required = true,
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = LoginRequest.class),
            examples = @ExampleObject(
                name = "Customer login",
                value = """
                        {
                          "email": "john@example.com",
                          "password": "Secure@123!"
                        }"""
            )
        )
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Returns access token, refresh token and user profile"),
        @ApiResponse(responseCode = "401", description = "Invalid credentials"),
        @ApiResponse(
                responseCode = "403",
                description = "Email not verified yet, or CORS rejected — see Auth tag description")
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login() {
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Login with a Google ID token (from Google Identity Services)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Google account verified, tokens returned"),
        @ApiResponse(responseCode = "400", description = "Invalid or expired Google credential")
    })
    @PostMapping("/google")
    public ResponseEntity<AuthResponse> googleAuth(@Valid @org.springframework.web.bind.annotation.RequestBody GoogleAuthRequest request) {
        return ResponseEntity.ok(authService.googleAuth(request.getCredential()));
    }

    @Operation(summary = "Send a password-reset link to the given email address")
    @ApiResponse(responseCode = "200", description = "Reset link sent (or silently ignored if email is not registered)")
    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @org.springframework.web.bind.annotation.RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.getEmail());
        return ResponseEntity.ok(new MessageResponse("If that email is registered, a reset link has been sent."));
    }

    @Operation(summary = "Reset password using the one-time token from the reset link")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Password updated"),
        @ApiResponse(responseCode = "400", description = "Invalid or expired reset token")
    })
    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @org.springframework.web.bind.annotation.RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(new MessageResponse("Password updated successfully."));
    }

    @Operation(summary = "Verify email using the token from the verification link")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Email verified"),
        @ApiResponse(responseCode = "400", description = "Invalid or expired token")
    })
    @GetMapping("/verify-email")
    public ResponseEntity<MessageResponse> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(new MessageResponse("Your email has been verified. You can sign in now."));
    }

    @Operation(summary = "Resend the verification email (same response whether or not the email exists)")
    @ApiResponse(responseCode = "200", description = "Generic success message")
    @PostMapping("/resend-verification")
    public ResponseEntity<MessageResponse> resendVerification(
            @Valid @org.springframework.web.bind.annotation.RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request.getEmail());
        return ResponseEntity.ok(new MessageResponse(
                "If an account exists for that address and is not yet verified, a new verification email has been sent."));
    }

    @Operation(summary = "Refresh the access token using a valid refresh token")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "New token pair returned"),
        @ApiResponse(responseCode = "400", description = "Invalid or expired refresh token")
    })
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @org.springframework.web.bind.annotation.RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @Operation(summary = "Logout — invalidates the access token and deletes the refresh token")
    @ApiResponse(responseCode = "204", description = "Logged out")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String accessToken,
            @org.springframework.web.bind.annotation.RequestBody(required = false) RefreshTokenRequest request) {
        authService.logout(accessToken, request != null ? request.getRefreshToken() : null);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get the currently authenticated user's profile (session rehydration)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Current user profile"),
        @ApiResponse(responseCode = "401", description = "No valid token")
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(userService.findById(UUID.fromString(principal.getUsername())));
    }
}
