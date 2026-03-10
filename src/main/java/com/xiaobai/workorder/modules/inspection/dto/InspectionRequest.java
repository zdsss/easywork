package com.xiaobai.workorder.modules.inspection.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class InspectionRequest {
    @NotNull(message = "Work order ID is required")
    private Long workOrderId;

    private Long operationId;

    @NotNull(message = "Inspection result is required")
    private String inspectionResult;  // PASSED or FAILED

    private BigDecimal inspectedQuantity;
    private BigDecimal qualifiedQuantity;
    private BigDecimal defectQuantity;
    private String defectReason;
    private String notes;
}
