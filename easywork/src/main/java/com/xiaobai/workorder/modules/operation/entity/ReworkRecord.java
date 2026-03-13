package com.xiaobai.workorder.modules.operation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("rework_records")
public class ReworkRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workOrderId;
    private Long originalOperationId;
    private Long reworkOperationId;
    private BigDecimal reworkQuantity;
    private String reworkReason;
    private Integer reworkTimes;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
