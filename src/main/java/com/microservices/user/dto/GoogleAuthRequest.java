package com.microservices.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Google ID token obtained from Google Identity Services (client-side sign-in)")
public class GoogleAuthRequest {

    @NotBlank(message = "Google credential is required")
    @Schema(
        description = "JWT ID token from Google Identity Services (window.google.accounts.id.initialize callback)",
        example = "eyJhbGciOiJSUzI1NiIsImtpZCI6Ii4uLiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwiZW1haWwiOiJ1c2VyQGdtYWlsLmNvbSJ9.signature"
    )
    private String credential;
}
