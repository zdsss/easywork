package com.xiaobai.workorder.modules.report.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ReportRequest {
    @NotNull(message = "Operation ID is required")
    private Long operationId;

    @DecimalMin(value = "0.01", message = "Reported quantity must be greater than zero")
    private BigDecimal reportedQuantity;

    @DecimalMin(value = "0", inclusive = true, message = "Qualified quantity cannot be negative")
    private BigDecimal qualifiedQuantity;

    @DecimalMin(value = "0", inclusive = true, message = "Defect quantity cannot be negative")
    private BigDecimal defectQuantity;

    private String deviceCode;
    private String notes;
}
