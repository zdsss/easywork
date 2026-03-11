package com.xiaobai.workorder.modules.call.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("call_records")
public class CallRecord {

    @TableId(type = IdType.AUTO)
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

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
