package com.xiaobai.workorder.modules.mesintegration.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class MesSyncLogDTO {
    private Long id;
    private String syncType;
    private String direction;
    private String sourceSystem;
    private String targetSystem;
    private String businessKey;
    private String status;
    private Integer retryCount;
    private Integer maxRetries;
    private String errorMessage;
    private LocalDateTime syncedAt;
    private LocalDateTime createdAt;
}
