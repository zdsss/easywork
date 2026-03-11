package com.xiaobai.workorder.modules.mesintegration.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mes_order_mappings")
public class MesOrderMapping {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long localOrderId;
    private String localOrderNumber;

    /**
     * ID assigned by the external MES system
     */
    private String mesOrderId;

    /**
     * Order number in the external MES system
     */
    private String mesOrderNumber;

    /**
     * PENDING / SYNCED / FAILED
     */
    private String syncStatus;

    private LocalDateTime lastSyncedAt;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
