package com.xiaobai.workorder.modules.report.service;

import com.xiaobai.workorder.common.enums.DependencyType;
import com.xiaobai.workorder.common.enums.WorkOrderStatus;
import com.xiaobai.workorder.common.enums.WorkOrderType;
import com.xiaobai.workorder.common.exception.BusinessException;
import com.xiaobai.workorder.modules.mesintegration.event.WorkOrderStatusChangedEvent;
import com.xiaobai.workorder.modules.operation.entity.Operation;
import com.xiaobai.workorder.modules.operation.entity.OperationDependency;
import com.xiaobai.workorder.modules.operation.repository.OperationDependencyMapper;
import com.xiaobai.workorder.modules.operation.repository.OperationMapper;
import com.xiaobai.workorder.modules.workorder.entity.WorkOrder;
import com.xiaobai.workorder.modules.workorder.repository.WorkOrderMapper;
import com.xiaobai.workorder.modules.workorder.statemachine.WorkOrderStateMachine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class WorkStartServiceTest {

    @Mock OperationMapper operationMapper;
    @Mock WorkOrderMapper workOrderMapper;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock OperationDependencyMapper operationDependencyMapper;
    @Mock WorkOrderStateMachine stateMachine;

    @InjectMocks WorkStartService workStartService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        // Default: state machine allows all transitions (override in individual tests as needed)
        when(stateMachine.canTransition(any(), any(), any())).thenReturn(true);
    }

    @Test
    void startWork_notStartedOperation_setsStatusToStarted() {
        Operation op = buildOperation(1L, 1L, "NOT_STARTED", BigDecimal.TEN);
        WorkOrder wo = buildWorkOrder(1L, WorkOrderStatus.NOT_STARTED);
        when(operationMapper.selectById(1L)).thenReturn(op);
        when(workOrderMapper.selectById(1L)).thenReturn(wo);

        workStartService.startWork(1L, 10L);

        assertThat(op.getStatus()).isEqualTo("STARTED");
        verify(operationMapper).updateById((Operation) any(Operation.class));
    }

    @Test
    void startWork_firstOperation_updatesWorkOrderToStarted() {
        Operation op = buildOperation(1L, 1L, "NOT_STARTED", BigDecimal.TEN);
        WorkOrder wo = buildWorkOrder(1L, WorkOrderStatus.NOT_STARTED);
        when(operationMapper.selectById(1L)).thenReturn(op);
        when(workOrderMapper.selectById(1L)).thenReturn(wo);

        workStartService.startWork(1L, 10L);

        assertThat(wo.getStatus()).isEqualTo(WorkOrderStatus.STARTED);
        verify(workOrderMapper).updateById((WorkOrder) any(WorkOrder.class));

        ArgumentCaptor<WorkOrderStatusChangedEvent> cap = ArgumentCaptor.forClass(WorkOrderStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        assertThat(cap.getValue().getPreviousStatus()).isEqualTo("NOT_STARTED");
        assertThat(cap.getValue().getCurrentStatus()).isEqualTo("STARTED");
    }

    @Test
    void startWork_alreadyStartedOperation_throwsBusinessException() {
        Operation op = buildOperation(1L, 1L, "STARTED", BigDecimal.TEN);
        when(operationMapper.selectById(1L)).thenReturn(op);

        assertThatThrownBy(() -> workStartService.startWork(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot be started");
    }

    @Test
    void startWork_noDependencies_startsNormally() {
        Operation op = buildOperation(1L, 1L, "NOT_STARTED", BigDecimal.TEN);
        WorkOrder wo = buildWorkOrder(1L, WorkOrderStatus.NOT_STARTED);
        when(operationMapper.selectById(1L)).thenReturn(op);
        when(workOrderMapper.selectById(1L)).thenReturn(wo);
        // No dependencies — Mockito default returns empty list for selectList

        workStartService.startWork(1L, 10L);

        assertThat(op.getStatus()).isEqualTo("STARTED");
    }

    @Test
    void startWork_predecessorNotComplete_throwsException() {
        Operation op = buildOperation(2L, 1L, "NOT_STARTED", BigDecimal.TEN);
        Operation predecessor = buildOperation(1L, 1L, "STARTED", BigDecimal.TEN); // not complete

        OperationDependency dep = new OperationDependency();
        dep.setOperationId(2L);
        dep.setPredecessorOperationId(1L);
        dep.setDependencyType(DependencyType.SERIAL);
        dep.setDeleted(0);

        when(operationMapper.selectById(2L)).thenReturn(op);
        when(operationDependencyMapper.selectList(any())).thenReturn(List.of(dep));
        when(operationMapper.selectById(1L)).thenReturn(predecessor);

        assertThatThrownBy(() -> workStartService.startWork(2L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未完成");
    }

    @Test
    void startWork_parallelDependency_notBlocked() {
        Operation op = buildOperation(2L, 1L, "NOT_STARTED", BigDecimal.TEN);
        WorkOrder wo = buildWorkOrder(1L, WorkOrderStatus.NOT_STARTED);

        OperationDependency dep = new OperationDependency();
        dep.setOperationId(2L);
        dep.setPredecessorOperationId(1L);
        dep.setDependencyType(DependencyType.PARALLEL); // PARALLEL does not block
        dep.setDeleted(0);

        when(operationMapper.selectById(2L)).thenReturn(op);
        when(operationDependencyMapper.selectList(any())).thenReturn(List.of(dep));
        when(workOrderMapper.selectById(1L)).thenReturn(wo);

        // Should NOT throw: PARALLEL dependency doesn't block start
        workStartService.startWork(2L, 10L);

        assertThat(op.getStatus()).isEqualTo("STARTED");
    }

    @Test
    void startWork_operationNotFound_throwsBusinessException() {
        when(operationMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> workStartService.startWork(99L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Operation not found");
    }

    @Test
    void startWork_workOrderAlreadyStarted_doesNotUpdateWorkOrder() {
        Operation op = buildOperation(1L, 1L, "NOT_STARTED", BigDecimal.TEN);
        WorkOrder wo = buildWorkOrder(1L, WorkOrderStatus.STARTED); // already started
        when(operationMapper.selectById(1L)).thenReturn(op);
        when(workOrderMapper.selectById(1L)).thenReturn(wo);

        workStartService.startWork(1L, 10L);

        // WorkOrder was already STARTED, so no update should happen
        verify(workOrderMapper, never()).updateById(any(WorkOrder.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private Operation buildOperation(Long id, Long workOrderId, String status, BigDecimal plannedQty) {
        Operation op = new Operation();
        op.setId(id);
        op.setWorkOrderId(workOrderId);
        op.setStatus(status);
        op.setPlannedQuantity(plannedQty);
        op.setCompletedQuantity(BigDecimal.ZERO);
        op.setDeleted(0);
        return op;
    }

    private WorkOrder buildWorkOrder(Long id, WorkOrderStatus status) {
        WorkOrder wo = new WorkOrder();
        wo.setId(id);
        wo.setStatus(status);
        wo.setOrderType(WorkOrderType.PRODUCTION);
        wo.setPlannedQuantity(BigDecimal.TEN);
        wo.setCompletedQuantity(BigDecimal.ZERO);
        wo.setDeleted(0);
        return wo;
    }
}
