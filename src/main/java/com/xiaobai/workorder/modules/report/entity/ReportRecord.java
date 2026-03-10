package com.xiaobai.workorder.modules.report.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("report_records")
public class ReportRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long operationId;
    private Long workOrderId;
    private Long userId;
    private Long deviceId;
    private BigDecimal reportedQuantity;
    private BigDecimal qualifiedQuantity;
    private BigDecimal defectQuantity;
    private LocalDateTime reportTime;
    private Boolean isUndone;
    private LocalDateTime undoTime;
    private String undoReason;
    private String notes;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
