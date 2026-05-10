package com.microservices.user.dto;

import com.microservices.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.UUID;

@Data
@Schema(description = "Fields to update on an existing user. All fields are optional — only provided fields are changed.")
public class UpdateUserRequest {

    @Schema(
        description = "New role to assign to the user",
        example = "BRANCH_MANAGER",
        allowableValues = {"CUSTOMER", "KITCHEN_STAFF", "BRANCH_MANAGER", "HEAD_OFFICE_ADMIN"},
        nullable = true
    )
    private User.Role role;

    @Schema(
        description = "Branch to assign the user to. Set null to remove branch association.",
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6",
        nullable = true
    )
    private UUID branchId;

    @Schema(
        description = "Whether the user account is active. Set false to deactivate.",
        example = "true",
        nullable = true
    )
    private Boolean isActive;
}
