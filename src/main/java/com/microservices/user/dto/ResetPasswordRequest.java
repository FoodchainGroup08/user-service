package com.microservices.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ResetPasswordRequest {

    @NotBlank(message = "Reset token is required")
    private String token;

    @NotBlank(message = "New password is required")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&_\\-#^()+=])[A-Za-z\\d@$!%*?&_\\-#^()+=]{8,}$",
        message = "Password must be at least 8 characters and contain at least one uppercase letter, one lowercase letter, one digit, and one special character"
    )
    private String newPassword;
}
