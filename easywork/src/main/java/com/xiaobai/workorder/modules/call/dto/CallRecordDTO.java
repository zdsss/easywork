package com.xiaobai.workorder.modules.call.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CallRecordDTO {
    private Long id;
    private Long workOrderId;
    private Long operationId;
    private String callType;
    private Long callerId;
    private Long handlerId;
    private String status;
    private LocalDateTime callTime;
    private LocalDateTime handleTime;
    private LocalDateTime completeTime;
    private String description;
    private String handleResult;
    private String notes;
}
