package com.xiaobai.workorder.modules.workorder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class AssignWorkOrderRequest {
    @NotNull(message = "Operation ID is required")
    private Long operationId;

    @NotBlank(message = "Assignment type is required")
    private String assignmentType;  // USER or TEAM

    private List<Long> userIds;
    private List<Long> teamIds;
}
