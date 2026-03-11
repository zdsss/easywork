package com.xiaobai.workorder.modules.mesintegration.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaobai.workorder.common.response.ApiResponse;
import com.xiaobai.workorder.modules.mesintegration.dto.MesSyncLogDTO;
import com.xiaobai.workorder.modules.mesintegration.dto.MesSyncStatsDTO;
import com.xiaobai.workorder.modules.mesintegration.service.MesSyncQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Admin console APIs for monitoring the MES integration health.
 */
@Tag(name = "Admin - MES Integration", description = "Monitor sync logs and stats")
@RestController
@RequestMapping("/api/admin/mes-integration")
@RequiredArgsConstructor
public class AdminMesIntegrationController {

    private final MesSyncQueryService queryService;

    @Operation(summary = "Get sync statistics overview")
    @GetMapping("/stats")
    public ApiResponse<MesSyncStatsDTO> getStats() {
        return ApiResponse.success(queryService.getStats());
    }

    @Operation(
            summary = "List sync logs with optional filters",
            description = "Filter by syncType (WORK_ORDER_IMPORT, REPORT_PUSH, STATUS_PUSH, " +
                          "INSPECTION_PUSH), status (PENDING, SUCCESS, FAILED, RETRYING), " +
                          "or direction (INBOUND, OUTBOUND).")
    @GetMapping("/logs")
    public ApiResponse<Page<MesSyncLogDTO>> listLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String syncType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String direction) {
        return ApiResponse.success(queryService.listLogs(page, size, syncType, status, direction));
    }
}
