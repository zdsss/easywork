package com.xiaobai.workorder.modules.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.xiaobai.workorder.common.enums.DependencyType;
import com.xiaobai.workorder.common.enums.WorkOrderStatus;
import com.xiaobai.workorder.common.enums.WorkOrderType;
import com.xiaobai.workorder.common.exception.BusinessException;
import com.xiaobai.workorder.modules.audit.aspect.Auditable;
import com.xiaobai.workorder.modules.mesintegration.event.ReportRecordSavedEvent;
import com.xiaobai.workorder.modules.mesintegration.event.WorkOrderStatusChangedEvent;
import com.xiaobai.workorder.modules.operation.entity.Operation;
import com.xiaobai.workorder.modules.operation.entity.OperationDependency;
import com.xiaobai.workorder.modules.operation.repository.OperationDependencyMapper;
import com.xiaobai.workorder.modules.operation.repository.OperationMapper;
import com.xiaobai.workorder.modules.report.dto.ReportRequest;
import com.xiaobai.workorder.modules.report.dto.UndoReportRequest;
import com.xiaobai.workorder.modules.report.entity.ReportRecord;
import com.xiaobai.workorder.modules.report.repository.ReportRecordMapper;
import com.xiaobai.workorder.modules.workorder.entity.WorkOrder;
import com.xiaobai.workorder.modules.workorder.repository.WorkOrderMapper;
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
    private final OperationDependencyMapper operationDependencyMapper;
    private final WorkOrderProperties workOrderProperties;

    @Transactional
    @Auditable(operation = "START_WORK", targetType = "OPERATION")
    public ReportRecord startWork(Long operationId, Long userId) {
        Operation operation = getOperationOrThrow(operationId);

        if (!"NOT_STARTED".equals(operation.getStatus())) {
            throw new BusinessException("Operation cannot be started, current status: " + operation.getStatus());
        }

        // Check SERIAL predecessor completion before allowing start
        checkPredecessors(operation);

        operation.setStatus("STARTED");
        try {
            operationMapper.updateById(operation);
        } catch (MybatisPlusException e) {
            throw new BusinessException("Operation was modified by another user, please retry");
        }

        updateWorkOrderStatusOnStart(operation.getWorkOrderId());

        log.info("Operation {} started by user {}", operationId, userId);
        return null;
    }

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
            if (!"STARTED".equals(operation.getStatus())) {
                throw new BusinessException("Operation must be started before reporting, current status: " + operation.getStatus());
            }
        } else {
            if (!"STARTED".equals(operation.getStatus()) && !"NOT_STARTED".equals(operation.getStatus())) {
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
            operation.setStatus("REPORTED");
        } else {
            operation.setStatus("STARTED");
        }
        operationMapper.updateById(operation);

        // Update work order based on order type
        updateWorkOrderOnReport(operation.getWorkOrderId());

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
            operation.setStatus("NOT_STARTED");
        } else {
            operation.setStatus("STARTED");
        }
        operationMapper.updateById(operation);

        // Recalculate work order
        updateWorkOrderOnReport(operation.getWorkOrderId());

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

    /**
     * Check that all SERIAL predecessors of this operation are in REPORTED or COMPLETED state.
     * PARALLEL dependencies do not block start.
     */
    private void checkPredecessors(Operation operation) {
        List<OperationDependency> deps = operationDependencyMapper.selectList(
                new LambdaQueryWrapper<OperationDependency>()
                        .eq(OperationDependency::getOperationId, operation.getId())
                        .eq(OperationDependency::getDeleted, 0));

        for (OperationDependency dep : deps) {
            if (DependencyType.SERIAL != dep.getDependencyType()) {
                continue; // PARALLEL dependencies do not block start
            }
            Operation predecessor = operationMapper.selectById(dep.getPredecessorOperationId());
            if (predecessor == null || predecessor.getDeleted() == 1) {
                continue; // Predecessor deleted or not found, skip
            }
            String pStatus = predecessor.getStatus();
            if (!"REPORTED".equals(pStatus) && !"COMPLETED".equals(pStatus)
                    && !"INSPECTED".equals(pStatus) && !"TRANSPORTED".equals(pStatus)
                    && !"HANDLED".equals(pStatus)) {
                throw new BusinessException(
                        "前置工序 [" + predecessor.getOperationName() + "] 尚未完成，无法开工");
            }
        }
    }

    private void updateWorkOrderStatusOnStart(Long workOrderId) {
        WorkOrder workOrder = workOrderMapper.selectById(workOrderId);
        if (workOrder != null && WorkOrderStatus.NOT_STARTED == workOrder.getStatus()) {
            WorkOrderStatus previousStatus = workOrder.getStatus();
            workOrder.setStatus(WorkOrderStatus.STARTED);
            workOrder.setActualStartTime(LocalDateTime.now());
            workOrderMapper.updateById(workOrder);

            eventPublisher.publishEvent(new WorkOrderStatusChangedEvent(
                    this, workOrderId, previousStatus.name(), WorkOrderStatus.STARTED.name(), null));
        }
    }

    private void updateWorkOrderOnReport(Long workOrderId) {
        WorkOrder workOrder = workOrderMapper.selectById(workOrderId);
        if (workOrder == null) return;

        List<Operation> operations = operationMapper.findByWorkOrderId(workOrderId);
        boolean allReported = operations.stream()
                .allMatch(op -> "REPORTED".equals(op.getStatus())
                        || "INSPECTED".equals(op.getStatus())
                        || "TRANSPORTED".equals(op.getStatus())
                        || "HANDLED".equals(op.getStatus()));

        BigDecimal totalCompleted = operations.stream()
                .map(Operation::getCompletedQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        workOrder.setCompletedQuantity(totalCompleted);

        if (allReported) {
            WorkOrderStatus previousStatus = workOrder.getStatus();
            WorkOrderStatus newStatus = getEffectiveCompletedStatus(workOrder);
            workOrder.setStatus(newStatus);
            workOrderMapper.updateById(workOrder);

            eventPublisher.publishEvent(new WorkOrderStatusChangedEvent(
                    this, workOrderId, previousStatus.name(), newStatus.name(), null));
        } else {
            workOrderMapper.updateById(workOrder);
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
