package com.microservices.user.dto;

import com.microservices.user.entity.User;
import lombok.Data;

import java.util.UUID;

@Data
public class UpdateUserRequest {

    private User.Role role;
    private UUID branchId;
    private Boolean isActive;
}
