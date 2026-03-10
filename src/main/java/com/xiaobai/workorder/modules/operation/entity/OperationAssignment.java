package com.xiaobai.workorder.modules.operation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("operation_assignments")
public class OperationAssignment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long operationId;
    private String assignmentType;  // USER or TEAM
    private Long userId;
    private Long teamId;
    private LocalDateTime assignedAt;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
