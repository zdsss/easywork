package com.xiaobai.workorder.modules.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.xiaobai.workorder.common.enums.OperationStatus;
import com.xiaobai.workorder.common.enums.WorkOrderStatus;
import com.xiaobai.workorder.common.enums.WorkOrderType;
import com.xiaobai.workorder.common.exception.BusinessException;
import com.xiaobai.workorder.modules.audit.aspect.Auditable;
import com.xiaobai.workorder.modules.mesintegration.event.ReportRecordSavedEvent;
import com.xiaobai.workorder.modules.mesintegration.event.WorkOrderStatusChangedEvent;
import com.xiaobai.workorder.modules.operation.entity.Operation;
import com.xiaobai.workorder.modules.operation.repository.OperationMapper;
import com.xiaobai.workorder.modules.report.dto.ReportRequest;
import com.xiaobai.workorder.modules.report.dto.UndoReportRequest;
import com.xiaobai.workorder.modules.report.entity.ReportRecord;
import com.xiaobai.workorder.modules.report.repository.ReportRecordMapper;
import com.xiaobai.workorder.modules.workorder.entity.WorkOrder;
import com.xiaobai.workorder.modules.workorder.repository.WorkOrderMapper;
import com.xiaobai.workorder.modules.workorder.statemachine.WorkOrderStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.xiaobai.workorder.config.WorkOrderProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRecordMapper reportRecordMapper;
    private final OperationMapper operationMapper;
    private final WorkOrderMapper workOrderMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final WorkOrderProperties workOrderProperties;
    private final WorkOrderStateMachine stateMachine;

    @Transactional
    @Auditable(operation = "REPORT_WORK", targetType = "OPERATION")
    public ReportRecord reportWork(ReportRequest request, Long userId, Long deviceId) {
        // Pessimistic lock: prevent concurrent over-reporting on the same operation
        Operation operation = operationMapper.selectByIdForUpdate(request.getOperationId());
        if (operation == null || operation.getDeleted() == 1) {
            throw new BusinessException("Operation not found: " + request.getOperationId());
        }

        // Determine force-start requirement per the work order's order type
        WorkOrder orderForTypeCheck = workOrderMapper.selectById(operation.getWorkOrderId());
        WorkOrderType orderType = orderForTypeCheck != null ? orderForTypeCheck.getOrderType() : null;
        boolean forceStart = workOrderProperties.isForceStartBeforeReport(orderType);

        if (forceStart) {
            if (OperationStatus.STARTED != operation.getStatus()) {
                throw new BusinessException("Operation must be started before reporting, current status: " + operation.getStatus());
            }
        } else {
            if (OperationStatus.STARTED != operation.getStatus() && OperationStatus.NOT_STARTED != operation.getStatus()) {
                throw new BusinessException("Operation cannot be reported, current status: " + operation.getStatus());
            }
        }

        BigDecimal alreadyReported = reportRecordMapper
                .sumReportedQuantityByOperationId(operation.getId());
        BigDecimal remaining = operation.getPlannedQuantity().subtract(alreadyReported);

        BigDecimal toReport = request.getReportedQuantity() != null
                ? request.getReportedQuantity()
                : remaining;

        if (toReport.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Reported quantity must be greater than zero");
        }
        if (toReport.compareTo(remaining) > 0) {
            throw new BusinessException(
                    "Reported quantity " + toReport + " exceeds remaining quantity " + remaining);
        }

        ReportRecord record = new ReportRecord();
        record.setOperationId(operation.getId());
        record.setWorkOrderId(operation.getWorkOrderId());
        record.setUserId(userId);
        record.setDeviceId(deviceId);
        record.setReportedQuantity(toReport);
        record.setQualifiedQuantity(request.getQualifiedQuantity() != null
                ? request.getQualifiedQuantity() : toReport);
        record.setDefectQuantity(request.getDefectQuantity() != null
                ? request.getDefectQuantity() : BigDecimal.ZERO);
        record.setReportTime(LocalDateTime.now());
        record.setIsUndone(false);
        record.setNotes(request.getNotes());
        reportRecordMapper.insert(record);

        // Publish report event for MES push (fires after transaction commits)
        eventPublisher.publishEvent(
                new ReportRecordSavedEvent(this, record.getId(),
                        operation.getWorkOrderId(), null));

        // Update operation completed quantity and status
        BigDecimal newCompleted = alreadyReported.add(toReport);
        operation.setCompletedQuantity(newCompleted);
        if (newCompleted.compareTo(operation.getPlannedQuantity()) >= 0) {
            operation.setStatus(OperationStatus.REPORTED);
        } else {
            operation.setStatus(OperationStatus.STARTED);
        }
        operationMapper.updateById(operation);

        // Atomically increment work order completed quantity and then check status transition
        int updated = workOrderMapper.addCompletedQuantity(orderForTypeCheck.getId(), toReport);
        if (updated == 0) {
            log.warn("addCompletedQuantity affected 0 rows for workOrder id={}", orderForTypeCheck.getId());
        }
        updateWorkOrderStatusOnReport(orderForTypeCheck.getId());

        log.info("Operation {} reported {} units by user {}", request.getOperationId(), toReport, userId);
        return record;
    }

    @Transactional
    @Auditable(operation = "UNDO_REPORT", targetType = "OPERATION")
    public void undoReport(UndoReportRequest request, Long userId) {
        Operation operation = getOperationOrThrow(request.getOperationId());

        ReportRecord latest = reportRecordMapper
                .findLatestByOperationIdAndUser(operation.getId(), userId)
                .orElseThrow(() -> new BusinessException("No report record found to undo"));

        latest.setIsUndone(true);
        latest.setUndoTime(LocalDateTime.now());
        latest.setUndoReason(request.getUndoReason());
        reportRecordMapper.updateById(latest);

        // Recalculate completed quantity
        BigDecimal newCompleted = reportRecordMapper
                .sumReportedQuantityByOperationId(operation.getId());
        operation.setCompletedQuantity(newCompleted);
        if (newCompleted.compareTo(BigDecimal.ZERO) == 0) {
            operation.setStatus(OperationStatus.NOT_STARTED);
        } else {
            operation.setStatus(OperationStatus.STARTED);
        }
        operationMapper.updateById(operation);

        // Atomically decrement work order completed quantity and then check status transition
        int updated = workOrderMapper.addCompletedQuantity(operation.getWorkOrderId(), latest.getReportedQuantity().negate());
        if (updated == 0) {
            log.warn("addCompletedQuantity affected 0 rows for workOrder id={}", operation.getWorkOrderId());
        }
        updateWorkOrderStatusOnReport(operation.getWorkOrderId());

        log.info("Report undone for operation {} by user {}", request.getOperationId(), userId);
    }

    public List<ReportRecord> getReportHistory(Long operationId) {
        return reportRecordMapper.findActiveByOperationId(operationId);
    }

    // ---------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------

    private Operation getOperationOrThrow(Long operationId) {
        Operation operation = operationMapper.selectById(operationId);
        if (operation == null || operation.getDeleted() == 1) {
            throw new BusinessException("Operation not found: " + operationId);
        }
        return operation;
    }

    private void updateWorkOrderStatusOnReport(Long workOrderId) {
        WorkOrder workOrder = workOrderMapper.selectById(workOrderId);
        if (workOrder == null) return;

        List<Operation> operations = operationMapper.findByWorkOrderId(workOrderId);
        boolean allReported = operations.stream()
                .allMatch(op -> OperationStatus.REPORTED == op.getStatus()
                        || OperationStatus.INSPECTED == op.getStatus()
                        || OperationStatus.TRANSPORTED == op.getStatus()
                        || OperationStatus.HANDLED == op.getStatus());

        if (allReported) {
            WorkOrderStatus previousStatus = workOrder.getStatus();
            WorkOrderStatus newStatus = getEffectiveCompletedStatus(workOrder);
            if (!stateMachine.canTransition(workOrder.getStatus(), newStatus, workOrder.getOrderType())) {
                throw new IllegalStateException("Invalid status transition: " + workOrder.getStatus() + " → " + newStatus);
            }
            workOrder.setStatus(newStatus);
            workOrderMapper.updateById(workOrder);

            eventPublisher.publishEvent(new WorkOrderStatusChangedEvent(
                    this, workOrderId, previousStatus.name(), newStatus.name(), null));
        }
    }

    /**
     * Returns the status a work order should transition to when all its operations are reported.
     * PRODUCTION orders go to REPORTED (awaiting quality inspection).
     * INSPECTION, TRANSPORT, and ANDON orders go directly to COMPLETED.
     */
    private WorkOrderStatus getEffectiveCompletedStatus(WorkOrder workOrder) {
        WorkOrderType orderType = workOrder.getOrderType();
        if (WorkOrderType.PRODUCTION == orderType) {
            return WorkOrderStatus.REPORTED;
        }
        // INSPECTION, TRANSPORT, ANDON — all complete directly
        return WorkOrderStatus.COMPLETED;
    }
}
