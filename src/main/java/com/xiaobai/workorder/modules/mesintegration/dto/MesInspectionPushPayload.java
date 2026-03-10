package com.xiaobai.workorder.modules.mesintegration.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Payload pushed to MES when an inspection result is submitted.
 */
@Data
public class MesInspectionPushPayload {
    private String mesOrderId;
    private String mesOrderNumber;
    private String localOrderNumber;
    private Long inspectionRecordId;
    private String inspectionResult;
    private BigDecimal inspectedQuantity;
    private BigDecimal qualifiedQuantity;
    private BigDecimal defectQuantity;
    private String defectReason;
    private String inspectorEmployeeNumber;
    private LocalDateTime inspectionTime;
}
