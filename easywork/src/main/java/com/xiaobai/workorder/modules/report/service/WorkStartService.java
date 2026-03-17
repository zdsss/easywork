package com.xiaobai.workorder.modules.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.xiaobai.workorder.common.enums.DependencyType;
import com.xiaobai.workorder.common.enums.WorkOrderStatus;
import com.xiaobai.workorder.common.exception.BusinessException;
import com.xiaobai.workorder.modules.audit.aspect.Auditable;
import com.xiaobai.workorder.modules.mesintegration.event.WorkOrderStatusChangedEvent;
import com.xiaobai.workorder.modules.operation.entity.Operation;
import com.xiaobai.workorder.modules.operation.entity.OperationDependency;
import com.xiaobai.workorder.modules.operation.repository.OperationDependencyMapper;
import com.xiaobai.workorder.modules.operation.repository.OperationMapper;
import com.xiaobai.workorder.modules.report.entity.ReportRecord;
import com.xiaobai.workorder.modules.workorder.entity.WorkOrder;
import com.xiaobai.workorder.modules.workorder.repository.WorkOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkStartService {

    private final OperationMapper operationMapper;
    private final WorkOrderMapper workOrderMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final OperationDependencyMapper operationDependencyMapper;

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
}
