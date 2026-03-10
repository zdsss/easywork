package com.xiaobai.workorder.modules.workorder.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("work_orders")
public class WorkOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNumber;
    private String orderType;
    private String productCode;
    private String productName;
    private BigDecimal plannedQuantity;
    private BigDecimal completedQuantity;
    private String status;
    private Integer priority;
    private LocalDateTime plannedStartTime;
    private LocalDateTime plannedEndTime;
    private LocalDateTime actualStartTime;
    private LocalDateTime actualEndTime;
    private String workshop;
    private String productionLine;
    private String notes;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;
}
