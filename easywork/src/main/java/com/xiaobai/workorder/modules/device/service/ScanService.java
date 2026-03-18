package com.xiaobai.workorder.modules.device.service;

import com.xiaobai.workorder.common.enums.OperationStatus;
import com.xiaobai.workorder.common.exception.BusinessException;
import com.xiaobai.workorder.modules.operation.entity.Operation;
import com.xiaobai.workorder.modules.operation.repository.OperationMapper;
import com.xiaobai.workorder.modules.workorder.dto.WorkOrderDTO;
import com.xiaobai.workorder.modules.workorder.service.WorkOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Resolves barcodes to work-order + operation pairs for device scan endpoints.
 *
 * <p>Two resolution strategies are supported:
 * <ol>
 *   <li><b>Operation barcode</b> — the barcode directly identifies an {@link Operation} by its
 *       {@code operationNumber}.  Precise targeting for multi-operation work orders.</li>
 *   <li><b>Work-order barcode</b> — the barcode identifies a {@link com.xiaobai.workorder.modules.workorder.entity.WorkOrder}
 *       by its {@code orderNumber}.  The service then picks the earliest eligible operation for
 *       the requesting user using the assignment-aware mapper queries.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class ScanService {

    private final OperationMapper operationMapper;
    private final WorkOrderService workOrderService;

    // ---------------------------------------------------------------
    // Public result carriers
    // ---------------------------------------------------------------

    /**
     * Result of a scan-to-start resolution.
     *
     * @param workOrderId the resolved work-order id
     * @param operation   the earliest NOT_STARTED operation for this user, or {@code null} when
     *                    no eligible operation was found (no-op start)
     */
    public record ScanStartResult(Long workOrderId, Operation operation) {}

    /**
     * Result of a scan-to-report resolution.
     *
     * @param workOrderId the resolved work-order id
     * @param operation   the earliest unfinished operation for this user, or {@code null} when
     *                    no eligible operation was found (no-op report)
     */
    public record ScanReportResult(Long workOrderId, Operation operation) {}

    // ---------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------

    /**
     * Resolves a barcode for a scan-to-start request.
     *
     * <p>Tries the operation barcode path first (precise targeting), then falls back to the
     * work-order barcode path (earliest NOT_STARTED operation by priority order).</p>
     *
     * @param barcode non-blank barcode string from the device
     * @param userId  the id of the currently authenticated worker
     * @return a {@link ScanStartResult} containing the resolved work-order id and optional operation
     * @throws BusinessException if the barcode resolves to nothing
     */
    public ScanStartResult resolveScanStart(String barcode, Long userId) {
        if (barcode == null || barcode.isBlank()) {
            throw new BusinessException("barcode is required");
        }

        // Try operation barcode first (precise targeting for multi-operation scenarios)
        Optional<Operation> byOpNumber = operationMapper.findByOperationNumber(barcode);
        if (byOpNumber.isPresent()) {
            Operation op = byOpNumber.get();
            // Only return the operation for starting if it is NOT_STARTED; otherwise no-op
            Operation eligibleOp = OperationStatus.NOT_STARTED == op.getStatus() ? op : null;
            return new ScanStartResult(op.getWorkOrderId(), eligibleOp);
        }

        // Fall back to work-order barcode: match by user/team assignment, pick earliest NOT_STARTED
        WorkOrderDTO workOrder = workOrderService.getWorkOrderByBarcode(barcode, userId);
        Long workOrderId = workOrder.getId();
        Operation op = resolveOperationForStart(userId, workOrderId);
        return new ScanStartResult(workOrderId, op);
    }

    /**
     * Resolves a barcode for a scan-to-report request.
     *
     * <p>Tries the operation barcode path first (precise targeting), then falls back to the
     * work-order barcode path (earliest unfinished operation by priority order).</p>
     *
     * @param barcode non-blank barcode string from the device
     * @param userId  the id of the currently authenticated worker
     * @return a {@link ScanReportResult} containing the resolved work-order id and optional operation
     * @throws BusinessException if the barcode resolves to nothing
     */
    public ScanReportResult resolveScanReport(String barcode, Long userId) {
        if (barcode == null || barcode.isBlank()) {
            throw new BusinessException("barcode is required");
        }

        // Try operation barcode first (precise targeting for multi-operation scenarios)
        Optional<Operation> byOpNumber = operationMapper.findByOperationNumber(barcode);
        if (byOpNumber.isPresent()) {
            Operation op = byOpNumber.get();
            // Don't report against an already-completed operation
            if (op != null && (OperationStatus.COMPLETED == op.getStatus() || OperationStatus.REPORTED == op.getStatus()
                    || OperationStatus.INSPECTED == op.getStatus() || OperationStatus.TRANSPORTED == op.getStatus() || OperationStatus.HANDLED == op.getStatus())) {
                op = null; // fall through to work-order path
            }
            if (op != null) {
                return new ScanReportResult(op.getWorkOrderId(), op);
            }
            // Operation barcode found but in terminal state: resolve next eligible op from the work order
            Long workOrderId = byOpNumber.get().getWorkOrderId();
            Operation nextOp = resolveOperationForReport(userId, workOrderId);
            return new ScanReportResult(workOrderId, nextOp);
        }

        // Fall back to work-order barcode: match by user/team assignment, pick earliest unfinished
        WorkOrderDTO workOrder = workOrderService.getWorkOrderByBarcode(barcode, userId);
        Long workOrderId = workOrder.getId();
        Operation op = resolveOperationForReport(userId, workOrderId);
        return new ScanReportResult(workOrderId, op);
    }

    // ---------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------

    /**
     * Resolves the earliest NOT_STARTED operation for the user (direct or team assignment).
     * PRD priority: 1) directly-assigned, 2) team-assigned.
     * Within each category, picks the front-most (lowest sequenceNumber) NOT_STARTED operation.
     */
    private Operation resolveOperationForStart(Long userId, Long workOrderId) {
        Operation op = operationMapper.findEarliestNotStartedByUserAndWorkOrder(userId, workOrderId);
        if (op != null) return op;
        return operationMapper.findEarliestNotStartedByTeamUserAndWorkOrder(userId, workOrderId);
    }

    /**
     * Resolves the earliest unfinished operation for the user (direct or team assignment).
     * Accepts STARTED or NOT_STARTED operations (excludes REPORTED/INSPECTED/TRANSPORTED/HANDLED).
     */
    private Operation resolveOperationForReport(Long userId, Long workOrderId) {
        Operation op = operationMapper.findEarliestUnfinishedByUserAndWorkOrder(userId, workOrderId);
        if (op != null) return op;
        return operationMapper.findEarliestUnfinishedByTeamUserAndWorkOrder(userId, workOrderId);
    }
}
