package com.xiaobai.workorder.modules.operation.controller;

import com.xiaobai.workorder.common.response.ApiResponse;
import com.xiaobai.workorder.modules.operation.entity.ReworkRecord;
import com.xiaobai.workorder.modules.operation.service.ReworkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Tag(name = "Admin - Rework", description = "Rework management")
@RestController
@RequestMapping("/api/admin/rework")
@RequiredArgsConstructor
public class AdminReworkController {

    private final ReworkService service;

    @Operation(summary = "Create rework record")
    @PostMapping
    public ApiResponse<ReworkRecord> createRework(
            @RequestParam Long workOrderId,
            @RequestParam Long originalOperationId,
            @RequestParam Long reworkOperationId,
            @RequestParam BigDecimal quantity,
            @RequestParam String reason) {
        return ApiResponse.success(service.createRework(workOrderId, originalOperationId,
                reworkOperationId, quantity, reason));
    }

    @Operation(summary = "Get rework records by work order")
    @GetMapping("/work-order/{workOrderId}")
    public ApiResponse<List<ReworkRecord>> getByWorkOrder(@PathVariable Long workOrderId) {
        return ApiResponse.success(service.getByWorkOrder(workOrderId));
    }
}
