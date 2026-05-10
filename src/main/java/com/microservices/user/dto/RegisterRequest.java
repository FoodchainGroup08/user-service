package com.microservices.user.dto;

import com.microservices.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.UUID;

@Data
@Schema(description = "Payload for creating a new user account")
public class RegisterRequest {

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

    @Schema(
        description = "User role. Defaults to CUSTOMER if omitted.",
        example = "CUSTOMER",
        allowableValues = {"CUSTOMER", "KITCHEN_STAFF", "BRANCH_MANAGER", "HEAD_OFFICE_ADMIN"}
    )
    private User.Role role;

    @Schema(
        description = "ID of the branch this user belongs to. Required for BRANCH_MANAGER and KITCHEN_STAFF. Leave null for CUSTOMER.",
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6",
        nullable = true
    )
    private UUID branchId;
}
