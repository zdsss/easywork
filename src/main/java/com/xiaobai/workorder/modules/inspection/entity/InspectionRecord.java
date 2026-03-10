package com.xiaobai.workorder.modules.inspection.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("inspection_records")
public class InspectionRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workOrderId;
    private Long operationId;
    private Long inspectorId;
    private String inspectionType;
    private String inspectionResult;
    private BigDecimal inspectedQuantity;
    private BigDecimal qualifiedQuantity;
    private BigDecimal defectQuantity;
    private String defectReason;
    private String status;
    private LocalDateTime inspectionTime;
    private String notes;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
