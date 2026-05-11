package com.microservices.user.dto;

import com.microservices.user.entity.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.UUID;

@Data
public class RegisterRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Email is required")
    @Pattern(
        regexp = "^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$",
        message = "Email must be a valid address (e.g., user@example.com)"
    )
    private String email;

    @NotBlank(message = "Password is required")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&_\\-#^()+=])[A-Za-z\\d@$!%*?&_\\-#^()+=]{8,}$",
        message = "Password must be at least 8 characters and contain at least one uppercase letter, one lowercase letter, one digit, and one special character"
    )
    private String password;

    private User.Role role;

    private UUID branchId;
}
