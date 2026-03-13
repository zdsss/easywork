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
import com.xiaobai.workorder.modules.inspection.entity.InspectionRecord;
import com.xiaobai.workorder.modules.inspection.service.InspectionService;
import com.xiaobai.workorder.modules.device.service.DeviceService;
import com.xiaobai.workorder.modules.operation.entity.ReworkRecord;
import com.xiaobai.workorder.modules.operation.repository.OperationMapper;
import com.xiaobai.workorder.modules.operation.service.ReworkService;
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

import java.math.BigDecimal;
import java.util.ArrayList;
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
    private final InspectionService inspectionService;
    private final ReworkService reworkService;
    private final OperationMapper operationMapper;
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

    @Operation(summary = "Scan barcode to start work - supports both work-order and operation barcodes")
    @PostMapping("/scan/start")
    public ApiResponse<WorkOrderDTO> scanStart(@RequestBody Map<String, String> body) {
        String barcode = body.get("barcode");
        if (barcode == null || barcode.isBlank()) {
            throw new BusinessException("barcode is required");
        }
        Long userId = securityUtils.getCurrentUserId();

        // Try operation barcode first (precise targeting for multi-operation scenarios)
        java.util.Optional<com.xiaobai.workorder.modules.operation.entity.Operation> byOpNumber = operationMapper.findByOperationNumber(barcode);
        if (byOpNumber.isPresent()) {
            com.xiaobai.workorder.modules.operation.entity.Operation op = byOpNumber.get();
            reportService.startWork(op.getId(), userId);
            return ApiResponse.success(workOrderService.getWorkOrderById(op.getWorkOrderId()));
        }

        // Fall back to work-order barcode: start the first unfinished operation
        WorkOrderDTO workOrder = workOrderService.getWorkOrderByBarcode(barcode, userId);
        workOrder.getOperations().stream()
                .filter(op -> "NOT_STARTED".equals(op.getStatus()) || "STARTED".equals(op.getStatus()))
                .findFirst()
                .ifPresent(op -> reportService.startWork(op.getId(), userId));
        return ApiResponse.success(workOrderService.getWorkOrderById(workOrder.getId()));
    }

    @Operation(summary = "Scan barcode to report work - supports both work-order and operation barcodes")
    @PostMapping("/scan/report")
    public ApiResponse<WorkOrderDTO> scanReport(@RequestBody Map<String, String> body) {
        String barcode = body.get("barcode");
        if (barcode == null || barcode.isBlank()) {
            throw new BusinessException("barcode is required");
        }
        Long userId = securityUtils.getCurrentUserId();

        // Try operation barcode first
        java.util.Optional<com.xiaobai.workorder.modules.operation.entity.Operation> byOpNumber = operationMapper.findByOperationNumber(barcode);
        if (byOpNumber.isPresent()) {
            com.xiaobai.workorder.modules.operation.entity.Operation op = byOpNumber.get();
            ReportRequest req = new ReportRequest();
            req.setOperationId(op.getId());
            reportService.reportWork(req, userId, null);
            return ApiResponse.success(workOrderService.getWorkOrderById(op.getWorkOrderId()));
        }

        // Fall back to work-order barcode
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

    @Operation(summary = "Batch start multiple operations")
    @PostMapping("/batch/start")
    public ApiResponse<List<Map<String, Object>>> batchStartWork(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Number> ids = (List<Number>) body.get("operationIds");
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("operationIds is required");
        }
        Long userId = securityUtils.getCurrentUserId();
        List<Map<String, Object>> results = new ArrayList<>();
        for (Number id : ids) {
            Long opId = id.longValue();
            try {
                reportService.startWork(opId, userId);
                results.add(Map.of("operationId", opId, "status", "OK"));
            } catch (Exception e) {
                results.add(Map.of("operationId", opId, "status", "ERROR", "message", e.getMessage()));
            }
        }
        return ApiResponse.success(results);
    }

    @Operation(summary = "Batch report multiple operations")
    @PostMapping("/batch/report")
    public ApiResponse<List<Map<String, Object>>> batchReportWork(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Number> ids = (List<Number>) body.get("operationIds");
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("operationIds is required");
        }
        Long userId = securityUtils.getCurrentUserId();
        List<Map<String, Object>> results = new ArrayList<>();
        for (Number id : ids) {
            Long opId = id.longValue();
            try {
                ReportRequest req = new ReportRequest();
                req.setOperationId(opId);
                reportService.reportWork(req, userId, null);
                results.add(Map.of("operationId", opId, "status", "OK"));
            } catch (Exception e) {
                results.add(Map.of("operationId", opId, "status", "ERROR", "message", e.getMessage()));
            }
        }
        return ApiResponse.success(results);
    }

    @Operation(summary = "Get latest inspection result for a work order")
    @GetMapping("/inspections/{workOrderId}")
    public ApiResponse<InspectionRecord> getInspectionDetail(@PathVariable Long workOrderId) {
        return ApiResponse.success(inspectionService.getLatestByWorkOrderId(workOrderId));
    }

    @Operation(summary = "Create rework record")
    @PostMapping("/rework")
    public ApiResponse<ReworkRecord> createRework(@RequestBody Map<String, Object> body) {
        Long workOrderId = getLong(body, "workOrderId");
        Long originalOperationId = getLong(body, "originalOperationId");
        BigDecimal reworkQuantity = getBigDecimal(body, "reworkQuantity");
        String reworkReason = (String) body.get("reworkReason");

        if (workOrderId == null || originalOperationId == null || reworkQuantity == null) {
            throw new BusinessException("workOrderId, originalOperationId, and reworkQuantity are required");
        }

        ReworkRecord record = reworkService.createRework(
            workOrderId, originalOperationId, null, reworkQuantity, reworkReason
        );
        return ApiResponse.success(record);
    }

    @Operation(summary = "Get rework history for a work order")
    @GetMapping("/rework/{workOrderId}")
    public ApiResponse<List<ReworkRecord>> getReworkHistory(@PathVariable Long workOrderId) {
        return ApiResponse.success(reworkService.getByWorkOrder(workOrderId));
    }

    private Long getLong(Map<String, Object> body, String key) {
        Object val = body.get(key);
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).longValue();
        return Long.parseLong(val.toString());
    }

    private BigDecimal getBigDecimal(Map<String, Object> body, String key) {
        Object val = body.get(key);
        if (val == null) return null;
        if (val instanceof BigDecimal) return (BigDecimal) val;
        return new BigDecimal(val.toString());
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
