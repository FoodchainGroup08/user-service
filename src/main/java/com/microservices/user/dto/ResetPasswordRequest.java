package com.microservices.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "Payload for resetting the password using a one-time token")
public class ResetPasswordRequest {

    @NotBlank(message = "Reset token is required")
    @Schema(
        description = "One-time reset token received via the password-reset email link",
        example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
    )
    private String token;

    @NotBlank(message = "New password is required")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#$!%*?&\\-_+=]).{8,}$",
        message = "Password must be at least 8 characters and include at least one uppercase letter, one lowercase letter, one digit, and one special character (@#$!%*?&-_+=)"
    )
    @Schema(
        description = "New strong password: min 8 characters, must include uppercase, lowercase, digit, and special character",
        example = "NewSecure@456!"
    )
    private String newPassword;
}
