package com.xiaobai.workorder.modules.workorder.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class WorkOrderDTO {
    private Long id;
    private String orderNumber;
    private String orderType;
    private String productCode;
    private String productName;
    private BigDecimal plannedQuantity;
    private BigDecimal completedQuantity;
    private BigDecimal remainingQuantity;
    private String status;
    private Integer priority;
    private LocalDateTime plannedStartTime;
    private LocalDateTime plannedEndTime;
    private LocalDateTime actualStartTime;
    private String workshop;
    private String productionLine;
    private String notes;
    private List<OperationSummary> operations;

    @Data
    public static class OperationSummary {
        private Long id;
        private String operationNumber;
        private String operationName;
        private String operationType;
        private Integer sequenceNumber;
        private BigDecimal plannedQuantity;
        private BigDecimal completedQuantity;
        private String status;
        private String stationCode;
        private String stationName;
    }
}
