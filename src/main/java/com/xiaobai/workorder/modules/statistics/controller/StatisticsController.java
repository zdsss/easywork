package com.xiaobai.workorder.modules.statistics.controller;

import com.xiaobai.workorder.common.response.ApiResponse;
import com.xiaobai.workorder.modules.statistics.dto.StatisticsDTO;
import com.xiaobai.workorder.modules.statistics.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Statistics", description = "Production statistics and dashboard")
@RestController
@RequestMapping("/api/admin/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;

    @Operation(summary = "Get dashboard statistics overview")
    @GetMapping("/dashboard")
    public ApiResponse<StatisticsDTO> getDashboard() {
        return ApiResponse.success(statisticsService.getDashboardStats());
    }
}
