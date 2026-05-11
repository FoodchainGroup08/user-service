package com.microservices.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.microservices.user.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private UUID id;
    private String name;
    private String email;
    private User.Role role;
    private UUID branchId;
    @JsonProperty("isActive")
    private boolean isActive;
    @JsonProperty("isEmailVerified")
    private boolean isEmailVerified;
    private LocalDateTime createdAt;

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .branchId(user.getBranchId())
                .isActive(user.isActive())
                .isEmailVerified(user.isEmailVerified())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
