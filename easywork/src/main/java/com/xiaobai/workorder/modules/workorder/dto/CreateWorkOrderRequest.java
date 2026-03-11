package com.xiaobai.workorder.modules.workorder.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CreateWorkOrderRequest {

    @NotBlank(message = "Order number is required")
    private String orderNumber;

    @NotBlank(message = "Order type is required")
    private String orderType;

    private String productCode;
    private String productName;

    @NotNull(message = "Planned quantity is required")
    @DecimalMin(value = "0.01", message = "Planned quantity must be positive")
    private BigDecimal plannedQuantity;

    private Integer priority = 0;
    private LocalDateTime plannedStartTime;
    private LocalDateTime plannedEndTime;
    private String workshop;
    private String productionLine;
    private String notes;
    private List<OperationInput> operations;

    @Data
    public static class OperationInput {
        @NotBlank(message = "Operation name is required")
        private String operationName;
        private String operationType = "PRODUCTION";
        private Integer sequenceNumber;
        private BigDecimal plannedQuantity;
        private String stationCode;
        private String stationName;
    }
}
