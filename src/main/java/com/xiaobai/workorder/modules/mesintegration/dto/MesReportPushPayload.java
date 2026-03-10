package com.xiaobai.workorder.modules.mesintegration.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Payload pushed to MES when a worker submits a report record.
 */
@Data
public class MesReportPushPayload {
    private String mesOrderId;
    private String mesOrderNumber;
    private String localOrderNumber;
    private Long operationId;
    private String operationNumber;
    private String operationName;
    private Long reportRecordId;
    private String employeeNumber;
    private String realName;
    private BigDecimal reportedQuantity;
    private BigDecimal qualifiedQuantity;
    private BigDecimal defectQuantity;
    private LocalDateTime reportTime;
    private String notes;
}
