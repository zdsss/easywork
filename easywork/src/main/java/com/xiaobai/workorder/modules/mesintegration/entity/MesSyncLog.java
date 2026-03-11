package com.xiaobai.workorder.modules.mesintegration.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mes_sync_logs")
public class MesSyncLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Sync type: WORK_ORDER_IMPORT, REPORT_PUSH, STATUS_PUSH, etc.
     */
    private String syncType;

    /**
     * Direction: INBOUND (MES→local) or OUTBOUND (local→MES)
     */
    private String direction;

    private String sourceSystem;
    private String targetSystem;

    /**
     * Business identifier, e.g. work order number
     */
    private String businessKey;

    /**
     * Serialized request payload (JSON)
     */
    private String payload;

    /**
     * Raw response body from external system
     */
    private String responseBody;

    /**
     * PENDING / SUCCESS / FAILED / RETRYING
     */
    private String status;

    private Integer retryCount;
    private Integer maxRetries;
    private String errorMessage;
    private LocalDateTime syncedAt;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
