package com.microservices.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ResendVerificationRequest {

    @NotBlank(message = "Email is required")
    @Pattern(
        regexp = "^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$",
        message = "Email must be a valid address (e.g., user@example.com)"
    )
    private String email;
}
