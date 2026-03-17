package com.xiaobai.workorder.modules.operation.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.xiaobai.workorder.common.enums.DependencyType;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("operation_dependencies")
public class OperationDependency {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long operationId;
    private Long predecessorOperationId;
    private DependencyType dependencyType;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
