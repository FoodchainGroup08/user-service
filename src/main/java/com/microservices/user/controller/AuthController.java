package com.microservices.user.controller;

import com.microservices.user.dto.*;
import com.microservices.user.service.AuthService;
import com.microservices.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Registration, login, token refresh, email verification, password reset and session endpoints")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @Operation(
        summary = "Register a new user",
        description = "Creates a new account and sends an email verification link (logged to console; plug in a real mailer via JavaMailSender/SendGrid)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "User created — verification email dispatched"),
        @ApiResponse(responseCode = "400", description = "Validation error or email already registered")
    })
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @Operation(
        summary = "Login with email and password",
        description = """
            Handled entirely by JwtAuthenticationFilter — this stub exists for Swagger documentation only.
            POST a JSON body with `email` and `password` fields.
            Both fields are validated against regex patterns:
            - email: RFC-style (user@domain.tld)
            - password: min 8 chars, ≥1 uppercase, ≥1 lowercase, ≥1 digit, ≥1 special character (@$!%*?&_-#^()+=)
            Returns access token (15 min), refresh token (7 days) and user profile on success.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Returns access token, refresh token and user profile"),
        @ApiResponse(responseCode = "400", description = "Email or password format invalid"),
        @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody(required = false) LoginRequest body) {
        // Handled by JwtAuthenticationFilter — this method body never executes
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Login with a Google ID token (from Google Identity Services)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Google account verified, tokens returned"),
        @ApiResponse(responseCode = "400", description = "Invalid or expired Google credential")
    })
    @PostMapping("/google")
    public ResponseEntity<AuthResponse> googleAuth(@Valid @RequestBody GoogleAuthRequest request) {
        return ResponseEntity.ok(authService.googleAuth(request.getCredential()));
    }

    @Operation(
        summary = "Refresh the access token using a valid refresh token",
        description = "Rotates both access and refresh tokens. The old refresh token is invalidated."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "New token pair returned"),
        @ApiResponse(responseCode = "400", description = "Invalid or expired refresh token")
    })
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @Operation(
        summary = "Logout — invalidates the access token and deletes the refresh token",
        description = "Pass the Bearer token in the Authorization header and the refresh token in the body. Both are optional but at least one should be provided."
    )
    @ApiResponse(responseCode = "204", description = "Logged out successfully")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String accessToken,
            @RequestBody(required = false) RefreshTokenRequest request) {
        authService.logout(accessToken, request != null ? request.getRefreshToken() : null);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Verify email address using the one-time token from the verification link",
        description = "Token is valid for 24 hours. Once used, it is invalidated."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Email verified successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid or expired verification token")
    })
    @GetMapping("/verify-email")
    public ResponseEntity<MessageResponse> verifyEmail(
            @Parameter(description = "One-time verification token from the email link", required = true)
            @RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(new MessageResponse("Email verified successfully."));
    }

    @Operation(
        summary = "Resend the email verification link",
        description = "Generates a new 24-hour verification token. Silently no-ops for unknown or already-verified emails."
    )
    @ApiResponse(responseCode = "200", description = "Verification link sent (or silently ignored)")
    @PostMapping("/resend-verification")
    public ResponseEntity<MessageResponse> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request.getEmail());
        return ResponseEntity.ok(new MessageResponse("If that email is registered and unverified, a new verification link has been sent."));
    }

    @Operation(summary = "Send a password-reset link to the given email address")
    @ApiResponse(responseCode = "200", description = "Reset link sent (or silently ignored if email is not registered)")
    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.getEmail());
        return ResponseEntity.ok(new MessageResponse("If that email is registered, a reset link has been sent."));
    }

    @Operation(summary = "Reset password using the one-time token from the reset link")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Password updated"),
        @ApiResponse(responseCode = "400", description = "Invalid or expired reset token, or password does not meet requirements")
    })
    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(new MessageResponse("Password updated successfully."));
    }

    @Operation(summary = "Get the currently authenticated user's profile (session rehydration)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Current user profile"),
        @ApiResponse(responseCode = "401", description = "No valid token")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(userService.findById(UUID.fromString(principal.getUsername())));
    }
}
