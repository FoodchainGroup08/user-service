package com.microservices.user.dto;

import lombok.Data;

@Data
public class UpdateProfileRequest {
    private String name;
    private String addressLine;
    private Double latitude;
    private Double longitude;
}
