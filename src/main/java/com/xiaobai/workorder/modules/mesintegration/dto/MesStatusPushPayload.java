package com.xiaobai.workorder.modules.mesintegration.dto;

import lombok.Data;

/**
 * Payload pushed to MES when a work order's status changes.
 */
@Data
public class MesStatusPushPayload {
    private String mesOrderId;
    private String mesOrderNumber;
    private String localOrderNumber;
    private String previousStatus;
    private String currentStatus;
    private String changedBy;
    private String changedAt;
    private String notes;
}
