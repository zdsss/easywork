package com.xiaobai.workorder.modules.device.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("devices")
public class Device {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String deviceCode;
    private String deviceName;
    private String deviceType;
    private String macAddress;
    private String status;
    private LocalDateTime lastLoginAt;
    private Long lastLoginUserId;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
