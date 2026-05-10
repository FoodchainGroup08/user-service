package com.microservices.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Refresh token payload for obtaining a new access token")
public class RefreshTokenRequest {

    @NotBlank(message = "Refresh token is required")
    @Schema(
        description = "The refresh token returned by /auth/login or /auth/refresh",
        example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyLWlkIn0.refresh_signature"
    )
    private String refreshToken;
}
