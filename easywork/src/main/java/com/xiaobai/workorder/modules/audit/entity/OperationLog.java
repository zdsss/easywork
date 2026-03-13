package com.xiaobai.workorder.modules.audit.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("operation_logs")
public class OperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String operationType;
    private String targetType;
    private Long targetId;
    private String beforeState;
    private String afterState;
    private String ipAddress;
    private String deviceId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
