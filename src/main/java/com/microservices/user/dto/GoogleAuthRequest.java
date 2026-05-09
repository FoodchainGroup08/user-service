package com.microservices.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GoogleAuthRequest {

    /** Google ID token returned by the Google Identity Services JS library. */
    @NotBlank(message = "Google credential is required")
    private String credential;
}
