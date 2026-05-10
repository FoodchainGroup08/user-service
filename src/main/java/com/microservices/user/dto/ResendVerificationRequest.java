package com.microservices.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Request another verification email")
public class ResendVerificationRequest {

    @NotBlank
    @Email
    @Schema(example = "user@example.com")
    private String email;
}
