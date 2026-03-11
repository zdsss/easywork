package com.xiaobai.workorder.modules.mesintegration.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Payload received from MES when pushing a work order into this system.
 * The MES system calls POST /api/mes/work-orders/import with this body.
 */
@Data
public class MesWorkOrderImportRequest {

    /** MES-side work order ID (used for idempotency and mapping) */
    private String mesOrderId;

    /** MES-side work order number */
    private String mesOrderNumber;

    /**
     * Order type: PRODUCTION / INSPECTION / TRANSPORT / ANDON
     */
    private String orderType;

    private String productCode;
    private String productName;
    private BigDecimal plannedQuantity;
    private Integer priority;
    private LocalDateTime plannedStartTime;
    private LocalDateTime plannedEndTime;
    private String workshop;
    private String productionLine;
    private String notes;

    private List<MesOperationInput> operations;

    @Data
    public static class MesOperationInput {
        private String mesOperationId;
        private String operationName;
        private String operationType;
        private Integer sequenceNumber;
        private BigDecimal plannedQuantity;
        private String stationCode;
        private String stationName;
        /** Employee numbers to assign directly */
        private List<String> assignedEmployeeNumbers;
        /** Team codes to assign */
        private List<String> assignedTeamCodes;
    }
}
