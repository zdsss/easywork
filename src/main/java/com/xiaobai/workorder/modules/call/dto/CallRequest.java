package com.xiaobai.workorder.modules.call.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CallRequest {
    @NotNull(message = "Work order ID is required")
    private Long workOrderId;

    private Long operationId;

    @NotBlank(message = "Call type is required")
    private String callType;

    private String description;
}
