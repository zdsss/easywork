package com.xiaobai.workorder.modules.device.controller;

import com.xiaobai.workorder.common.exception.BusinessException;
import com.xiaobai.workorder.common.response.ApiResponse;
import com.xiaobai.workorder.common.util.SecurityUtils;
import com.xiaobai.workorder.modules.auth.dto.LoginRequest;
import com.xiaobai.workorder.modules.auth.dto.LoginResponse;
import com.xiaobai.workorder.modules.auth.service.AuthService;
import com.xiaobai.workorder.modules.call.dto.CallRequest;
import com.xiaobai.workorder.modules.call.entity.CallRecord;
import com.xiaobai.workorder.modules.call.service.CallService;
import com.xiaobai.workorder.modules.device.service.DeviceService;
import com.xiaobai.workorder.modules.report.dto.ReportRequest;
import com.xiaobai.workorder.modules.report.dto.UndoReportRequest;
import com.xiaobai.workorder.modules.report.entity.ReportRecord;
import com.xiaobai.workorder.modules.report.service.ReportService;
import com.xiaobai.workorder.modules.workorder.dto.WorkOrderDTO;
import com.xiaobai.workorder.modules.workorder.service.WorkOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Device API", description = "APIs for handheld industrial devices")
@RestController
@RequestMapping("/api/device")
@RequiredArgsConstructor
public class DeviceController {

    private final AuthService authService;
    private final WorkOrderService workOrderService;
    private final ReportService reportService;
    private final CallService callService;
    private final DeviceService deviceService;
    private final SecurityUtils securityUtils;

    @Operation(summary = "Device login with employee number and password")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @Operation(summary = "Fetch work orders assigned to the logged-in worker")
    @GetMapping("/work-orders")
    public ApiResponse<List<WorkOrderDTO>> getAssignedWorkOrders() {
        Long userId = securityUtils.getCurrentUserId();
        return ApiResponse.success(workOrderService.getAssignedWorkOrders(userId));
    }

    @Operation(summary = "Start a work order operation")
    @PostMapping("/start")
    public ApiResponse<Void> startWork(@RequestBody Map<String, Long> body) {
        Long operationId = body.get("operationId");
        if (operationId == null) {
            throw new BusinessException("operationId is required");
        }
        Long userId = securityUtils.getCurrentUserId();
        reportService.startWork(operationId, userId);
        return ApiResponse.success("Operation started", null);
    }

    @Operation(summary = "Report completed work quantity")
    @PostMapping("/report")
    public ApiResponse<ReportRecord> reportWork(@Valid @RequestBody ReportRequest request) {
        Long userId = securityUtils.getCurrentUserId();
        Long deviceId = null;
        if (request.getDeviceCode() != null && !request.getDeviceCode().isBlank()) {
            try {
                deviceId = deviceService.findByCode(request.getDeviceCode()).getId();
            } catch (Exception ignored) {
                // deviceCode provided but not found — proceed without linking device
            }
        }
        ReportRecord record = reportService.reportWork(request, userId, deviceId);
        return ApiResponse.success(record);
    }

    @Operation(summary = "Scan barcode to start work - auto-resolves operation for worker")
    @PostMapping("/scan/start")
    public ApiResponse<WorkOrderDTO> scanStart(@RequestBody Map<String, String> body) {
        String barcode = body.get("barcode");
        if (barcode == null || barcode.isBlank()) {
            throw new BusinessException("barcode is required");
        }
        Long userId = securityUtils.getCurrentUserId();
        WorkOrderDTO workOrder = workOrderService.getWorkOrderByBarcode(barcode, userId);

        // Find and start the earliest unfinished operation for this user
        workOrder.getOperations().stream()
                .filter(op -> "NOT_STARTED".equals(op.getStatus()) || "STARTED".equals(op.getStatus()))
                .findFirst()
                .ifPresent(op -> reportService.startWork(op.getId(), userId));

        return ApiResponse.success(workOrderService.getWorkOrderById(workOrder.getId()));
    }

    @Operation(summary = "Scan barcode to report work - auto-resolves operation for worker")
    @PostMapping("/scan/report")
    public ApiResponse<WorkOrderDTO> scanReport(@RequestBody Map<String, String> body) {
        String barcode = body.get("barcode");
        if (barcode == null || barcode.isBlank()) {
            throw new BusinessException("barcode is required");
        }
        Long userId = securityUtils.getCurrentUserId();
        WorkOrderDTO workOrder = workOrderService.getWorkOrderByBarcode(barcode, userId);

        workOrder.getOperations().stream()
                .filter(op -> "STARTED".equals(op.getStatus()) || "NOT_STARTED".equals(op.getStatus()))
                .findFirst()
                .ifPresent(op -> {
                    ReportRequest req = new ReportRequest();
                    req.setOperationId(op.getId());
                    reportService.reportWork(req, userId, null);
                });

        return ApiResponse.success(workOrderService.getWorkOrderById(workOrder.getId()));
    }

    @Operation(summary = "Undo the last report for an operation")
    @PostMapping("/report/undo")
    public ApiResponse<Void> undoReport(@Valid @RequestBody UndoReportRequest request) {
        Long userId = securityUtils.getCurrentUserId();
        reportService.undoReport(request, userId);
        return ApiResponse.success("Report undone", null);
    }

    @Operation(summary = "Trigger Andon call")
    @PostMapping("/call/andon")
    public ApiResponse<CallRecord> callAndon(@RequestBody Map<String, Object> body) {
        Long userId = securityUtils.getCurrentUserId();
        CallRequest req = buildCallRequest(body, "ANDON");
        return ApiResponse.success(callService.createCall(req, userId));
    }

    @Operation(summary = "Call for inspection")
    @PostMapping("/call/inspection")
    public ApiResponse<CallRecord> callInspection(@RequestBody Map<String, Object> body) {
        Long userId = securityUtils.getCurrentUserId();
        CallRequest req = buildCallRequest(body, "INSPECTION");
        return ApiResponse.success(callService.createCall(req, userId));
    }

    @Operation(summary = "Call for transport/material handling")
    @PostMapping("/call/transport")
    public ApiResponse<CallRecord> callTransport(@RequestBody Map<String, Object> body) {
        Long userId = securityUtils.getCurrentUserId();
        CallRequest req = buildCallRequest(body, "TRANSPORT");
        return ApiResponse.success(callService.createCall(req, userId));
    }

    private CallRequest buildCallRequest(Map<String, Object> body, String callType) {
        CallRequest req = new CallRequest();
        if (body.get("workOrderId") == null) {
            throw new BusinessException("workOrderId is required");
        }
        req.setWorkOrderId(Long.parseLong(body.get("workOrderId").toString()));
        req.setCallType(callType);
        if (body.get("operationId") != null) {
            req.setOperationId(Long.parseLong(body.get("operationId").toString()));
        }
        if (body.get("description") != null) {
            req.setDescription(body.get("description").toString());
        }
        return req;
    }
}
