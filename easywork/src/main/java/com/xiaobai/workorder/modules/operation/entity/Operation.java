package com.xiaobai.workorder.modules.operation.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.xiaobai.workorder.common.enums.OperationStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("operations")
public class Operation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workOrderId;
    private String operationNumber;
    private String operationName;
    private String operationType;
    private Integer sequenceNumber;
    private BigDecimal plannedQuantity;
    private BigDecimal completedQuantity;
    private OperationStatus status;
    private Integer standardTime;
    private Integer actualTime;
    private String stationCode;
    private String stationName;
    private String notes;

    @Version
    private Integer version;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
