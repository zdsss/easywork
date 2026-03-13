package com.xiaobai.workorder.modules.audit.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaobai.workorder.common.response.ApiResponse;
import com.xiaobai.workorder.modules.audit.entity.OperationLog;
import com.xiaobai.workorder.modules.audit.repository.OperationLogMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin - Audit Logs", description = "Operation audit log queries")
@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
public class AdminAuditLogController {

    private final OperationLogMapper mapper;

    @Operation(summary = "Query audit logs")
    @GetMapping
    public ApiResponse<Page<OperationLog>> queryLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String targetType) {

        LambdaQueryWrapper<OperationLog> query = new LambdaQueryWrapper<>();
        if (userId != null) query.eq(OperationLog::getUserId, userId);
        if (targetType != null) query.eq(OperationLog::getTargetType, targetType);
        query.orderByDesc(OperationLog::getCreatedAt);

        return ApiResponse.success(mapper.selectPage(new Page<>(page, size), query));
    }
}
