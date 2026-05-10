package com.microservices.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.UUID;

@Data
@Schema(description = "Payload for creating a new customer account (role is always CUSTOMER)")
public class RegisterRequest {

    @NotBlank(message = "Name is required")
    @Schema(description = "User's full name", example = "John Doe")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Pattern(
        regexp = "^[a-zA-Z0-9][a-zA-Z0-9._%+\\-]*@[a-zA-Z0-9][a-zA-Z0-9.\\-]*\\.[a-zA-Z]{2,}$",
        message = "Email must be a valid address (e.g. user@example.com)"
    )
    @Schema(description = "Valid email address", example = "john@example.com")
    private String email;

    @NotBlank(message = "Password is required")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#$!%*?&\\-_+=]).{8,}$",
        message = "Password must be at least 8 characters and include at least one uppercase letter, one lowercase letter, one digit, and one special character (@#$!%*?&-_+=)"
    )
    @Schema(
        description = "Strong password: min 8 characters, must include uppercase, lowercase, digit, and special character (@#$!%*?&-_+=)",
        example = "Secure@123!"
    )
    private String password;

    @NotNull(message = "Branch is required")
    @Schema(
        description = "Branch the customer registers under — must exist (see GET /api/v1/branches).",
        example = "a0000001-0000-4000-8000-000000000001",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    private UUID branchId;
}
