package com.xiaobai.workorder.modules.workorder.controller;

import com.xiaobai.workorder.common.response.ApiResponse;
import com.xiaobai.workorder.common.response.PageResponse;
import com.xiaobai.workorder.common.util.SecurityUtils;
import com.xiaobai.workorder.modules.workorder.dto.AssignWorkOrderRequest;
import com.xiaobai.workorder.modules.workorder.dto.CreateWorkOrderRequest;
import com.xiaobai.workorder.modules.workorder.dto.WorkOrderDTO;
import com.xiaobai.workorder.modules.workorder.service.WorkOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin - Work Orders", description = "Admin APIs for work order management")
@RestController
@RequestMapping("/api/admin/work-orders")
@RequiredArgsConstructor
public class AdminWorkOrderController {

    private final WorkOrderService workOrderService;
    private final SecurityUtils securityUtils;

    @Operation(summary = "Create a new work order")
    @PostMapping
    public ApiResponse<WorkOrderDTO> createWorkOrder(
            @Valid @RequestBody CreateWorkOrderRequest request) {
        Long userId = securityUtils.getCurrentUserId();
        return ApiResponse.success(workOrderService.createWorkOrder(request, userId));
    }

    @Operation(summary = "List all work orders with optional status filter")
    @GetMapping
    public ApiResponse<List<WorkOrderDTO>> listWorkOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(workOrderService.listAllWorkOrders(page, size, status));
    }

    @Operation(summary = "Get work order detail by ID")
    @GetMapping("/{id}")
    public ApiResponse<WorkOrderDTO> getWorkOrder(@PathVariable Long id) {
        return ApiResponse.success(workOrderService.getWorkOrderById(id));
    }

    @Operation(summary = "Assign operation to users or teams")
    @PostMapping("/assign")
    public ApiResponse<Void> assignWorkOrder(@Valid @RequestBody AssignWorkOrderRequest request) {
        workOrderService.assignWorkOrder(request);
        return ApiResponse.success("Assignment successful", null);
    }
}
