package com.xiaobai.workorder.modules.report.service;

import com.xiaobai.workorder.common.enums.DependencyType;
import com.xiaobai.workorder.common.enums.WorkOrderStatus;
import com.xiaobai.workorder.common.enums.WorkOrderType;
import com.xiaobai.workorder.common.exception.BusinessException;
import com.xiaobai.workorder.config.WorkOrderProperties;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock ReportRecordMapper reportRecordMapper;
    @Mock OperationMapper operationMapper;
    @Mock WorkOrderMapper workOrderMapper;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock OperationDependencyMapper operationDependencyMapper;
    @Mock WorkOrderProperties workOrderProperties;

    @InjectMocks ReportService reportService;

    // ---------------------------------------------------------------
    // startWork tests
    // ---------------------------------------------------------------

    @Test
    void startWork_notStartedOperation_setsStatusToStarted() {
        Operation op = buildOperation(1L, 1L, "NOT_STARTED", BigDecimal.TEN);
        WorkOrder wo = buildWorkOrder(1L, WorkOrderStatus.NOT_STARTED);
        when(operationMapper.selectById(1L)).thenReturn(op);
        when(workOrderMapper.selectById(1L)).thenReturn(wo);

        reportService.startWork(1L, 10L);

        // Entity is modified in-place before updateById is called
        assertThat(op.getStatus()).isEqualTo("STARTED");
        verify(operationMapper).updateById((Operation) any(Operation.class));
    }

    @Test
    void startWork_firstOperation_updatesWorkOrderToStarted() {
        Operation op = buildOperation(1L, 1L, "NOT_STARTED", BigDecimal.TEN);
        WorkOrder wo = buildWorkOrder(1L, WorkOrderStatus.NOT_STARTED);
        when(operationMapper.selectById(1L)).thenReturn(op);
        when(workOrderMapper.selectById(1L)).thenReturn(wo);

        reportService.startWork(1L, 10L);

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

        assertThatThrownBy(() -> reportService.startWork(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot be started");
    }

    // ---------------------------------------------------------------
    // reportWork tests
    // ---------------------------------------------------------------

    @Test
    void reportWork_normalReport_insertsRecordAndUpdatesOperation() {
        Operation op = buildOperation(1L, 1L, "STARTED", BigDecimal.TEN);
        WorkOrder wo = buildWorkOrder(1L, WorkOrderStatus.STARTED);
        when(operationMapper.selectByIdForUpdate(1L)).thenReturn(op);
        when(reportRecordMapper.sumReportedQuantityByOperationId(1L)).thenReturn(BigDecimal.ZERO);
        when(workOrderMapper.selectById(1L)).thenReturn(wo);
        when(operationMapper.findByWorkOrderId(1L)).thenReturn(List.of(op));

        ReportRequest req = new ReportRequest();
        req.setOperationId(1L);
        req.setReportedQuantity(new BigDecimal("5"));

        ReportRecord result = reportService.reportWork(req, 10L, null);

        assertThat(result).isNotNull();
        assertThat(result.getReportedQuantity()).isEqualByComparingTo("5");
        verify(reportRecordMapper).insert((ReportRecord) any(ReportRecord.class));
    }

    @Test
    void reportWork_nullQuantity_usesRemainingQuantity() {
        Operation op = buildOperation(1L, 1L, "STARTED", BigDecimal.TEN);
        WorkOrder wo = buildWorkOrder(1L, WorkOrderStatus.STARTED);
        when(operationMapper.selectByIdForUpdate(1L)).thenReturn(op);
        when(reportRecordMapper.sumReportedQuantityByOperationId(1L)).thenReturn(new BigDecimal("3"));
        when(workOrderMapper.selectById(1L)).thenReturn(wo);
        when(operationMapper.findByWorkOrderId(1L)).thenReturn(List.of(op));

        ReportRequest req = new ReportRequest();
        req.setOperationId(1L);
        // reportedQuantity is null

        ReportRecord result = reportService.reportWork(req, 10L, null);

        // remaining = 10 - 3 = 7
        assertThat(result.getReportedQuantity()).isEqualByComparingTo("7");
    }

    @Test
    void reportWork_exceedsRemaining_throwsBusinessException() {
        Operation op = buildOperation(1L, 1L, "STARTED", BigDecimal.TEN);
        when(operationMapper.selectByIdForUpdate(1L)).thenReturn(op);
        when(reportRecordMapper.sumReportedQuantityByOperationId(1L)).thenReturn(new BigDecimal("8"));

        ReportRequest req = new ReportRequest();
        req.setOperationId(1L);
        req.setReportedQuantity(new BigDecimal("5")); // remaining is 2

        assertThatThrownBy(() -> reportService.reportWork(req, 10L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("exceeds remaining");
    }

    @Test
    void reportWork_zeroQuantity_throwsBusinessException() {
        Operation op = buildOperation(1L, 1L, "STARTED", BigDecimal.TEN);
        when(operationMapper.selectByIdForUpdate(1L)).thenReturn(op);
        when(reportRecordMapper.sumReportedQuantityByOperationId(1L)).thenReturn(BigDecimal.TEN);

        ReportRequest req = new ReportRequest();
        req.setOperationId(1L);
        req.setReportedQuantity(BigDecimal.ZERO);

        assertThatThrownBy(() -> reportService.reportWork(req, 10L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    void reportWork_completesOperation_setsStatusToReported() {
        Operation op = buildOperation(1L, 1L, "STARTED", BigDecimal.TEN);
        WorkOrder wo = buildWorkOrder(1L, WorkOrderStatus.STARTED);
        wo.setOrderType(WorkOrderType.PRODUCTION);
        when(operationMapper.selectByIdForUpdate(1L)).thenReturn(op);
        when(reportRecordMapper.sumReportedQuantityByOperationId(1L)).thenReturn(new BigDecimal("5"));
        when(workOrderMapper.selectById(1L)).thenReturn(wo);

        // After report, simulate all operations reported
        Operation reportedOp = buildOperation(1L, 1L, "REPORTED", BigDecimal.TEN);
        reportedOp.setCompletedQuantity(BigDecimal.TEN);
        when(operationMapper.findByWorkOrderId(1L)).thenReturn(List.of(reportedOp));

        ReportRequest req = new ReportRequest();
        req.setOperationId(1L);
        req.setReportedQuantity(new BigDecimal("5")); // 5 + 5 = 10 = plannedQty

        reportService.reportWork(req, 10L, null);

        // op was modified in-place: completed = 5+5=10 = planned, so REPORTED
        assertThat(op.getStatus()).isEqualTo("REPORTED");
    }

    @Test
    void reportWork_allOperationsReported_updatesWorkOrderToReported() {
        Operation op = buildOperation(1L, 1L, "STARTED", BigDecimal.TEN);
        WorkOrder wo = buildWorkOrder(1L, WorkOrderStatus.STARTED);
        wo.setOrderType(WorkOrderType.PRODUCTION);
        when(operationMapper.selectByIdForUpdate(1L)).thenReturn(op);
        when(reportRecordMapper.sumReportedQuantityByOperationId(1L)).thenReturn(BigDecimal.ZERO);
        when(workOrderMapper.selectById(1L)).thenReturn(wo);

        Operation reportedOp = buildOperation(1L, 1L, "REPORTED", BigDecimal.TEN);
        reportedOp.setCompletedQuantity(BigDecimal.TEN);
        when(operationMapper.findByWorkOrderId(1L)).thenReturn(List.of(reportedOp));

        ReportRequest req = new ReportRequest();
        req.setOperationId(1L);
        req.setReportedQuantity(BigDecimal.TEN);

        reportService.reportWork(req, 10L, null);

        assertThat(wo.getStatus()).isEqualTo(WorkOrderStatus.REPORTED);
    }

    @Test
    void reportWork_publishesReportRecordSavedEvent() {
        Operation op = buildOperation(1L, 1L, "STARTED", BigDecimal.TEN);
        WorkOrder wo = buildWorkOrder(1L, WorkOrderStatus.STARTED);
        when(operationMapper.selectByIdForUpdate(1L)).thenReturn(op);
        when(reportRecordMapper.sumReportedQuantityByOperationId(1L)).thenReturn(BigDecimal.ZERO);
        when(workOrderMapper.selectById(1L)).thenReturn(wo);
        when(operationMapper.findByWorkOrderId(1L)).thenReturn(List.of(op));

        ReportRequest req = new ReportRequest();
        req.setOperationId(1L);
        req.setReportedQuantity(new BigDecimal("3"));

        reportService.reportWork(req, 10L, null);

        verify(eventPublisher).publishEvent(any(ReportRecordSavedEvent.class));
    }

    // ---------------------------------------------------------------
    // undoReport tests
    // ---------------------------------------------------------------

    @Test
    void undoReport_validRecord_marksAsUndone() {
        Operation op = buildOperation(1L, 1L, "STARTED", BigDecimal.TEN);
        ReportRecord record = new ReportRecord();
        record.setId(100L);
        record.setReportedQuantity(new BigDecimal("5"));
        record.setIsUndone(false);

        WorkOrder wo = buildWorkOrder(1L, WorkOrderStatus.STARTED);
        when(operationMapper.selectById(1L)).thenReturn(op);
        when(reportRecordMapper.findLatestByOperationIdAndUser(1L, 10L)).thenReturn(Optional.of(record));
        when(reportRecordMapper.sumReportedQuantityByOperationId(1L)).thenReturn(BigDecimal.ZERO);
        when(workOrderMapper.selectById(1L)).thenReturn(wo);
        when(operationMapper.findByWorkOrderId(1L)).thenReturn(List.of(op));

        UndoReportRequest req = new UndoReportRequest();
        req.setOperationId(1L);
        req.setUndoReason("Mistake");

        reportService.undoReport(req, 10L);

        assertThat(record.getIsUndone()).isTrue();
        verify(reportRecordMapper).updateById((ReportRecord) any(ReportRecord.class));
    }

    @Test
    void undoReport_zeroRemaining_setsOperationToNotStarted() {
        Operation op = buildOperation(1L, 1L, "STARTED", BigDecimal.TEN);
        ReportRecord record = new ReportRecord();
        record.setId(100L);
        record.setReportedQuantity(new BigDecimal("5"));
        record.setIsUndone(false);

        WorkOrder wo = buildWorkOrder(1L, WorkOrderStatus.STARTED);
        when(operationMapper.selectById(1L)).thenReturn(op);
        when(reportRecordMapper.findLatestByOperationIdAndUser(1L, 10L)).thenReturn(Optional.of(record));
        when(reportRecordMapper.sumReportedQuantityByOperationId(1L)).thenReturn(BigDecimal.ZERO);
        when(workOrderMapper.selectById(1L)).thenReturn(wo);
        when(operationMapper.findByWorkOrderId(1L)).thenReturn(List.of(op));

        UndoReportRequest req = new UndoReportRequest();
        req.setOperationId(1L);

        reportService.undoReport(req, 10L);

        // After sum = 0, operation should revert to NOT_STARTED
        assertThat(op.getStatus()).isEqualTo("NOT_STARTED");
    }

    @Test
    void undoReport_noRecord_throwsBusinessException() {
        Operation op = buildOperation(1L, 1L, "STARTED", BigDecimal.TEN);
        when(operationMapper.selectById(1L)).thenReturn(op);
        when(reportRecordMapper.findLatestByOperationIdAndUser(1L, 10L)).thenReturn(Optional.empty());

        UndoReportRequest req = new UndoReportRequest();
        req.setOperationId(1L);

        assertThatThrownBy(() -> reportService.undoReport(req, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No report record found");
    }

    @Test
    void undoReport_operationNotFound_throwsBusinessException() {
        when(operationMapper.selectById(99L)).thenReturn(null);

        UndoReportRequest req = new UndoReportRequest();
        req.setOperationId(99L);

        assertThatThrownBy(() -> reportService.undoReport(req, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Operation not found");
    }

    // ---------------------------------------------------------------
    // orderType branching tests
    // ---------------------------------------------------------------

    @Test
    void testReportWork_InspectionOrder_DirectlyCompletes() {
        Operation op = buildOperation(1L, 1L, "STARTED", BigDecimal.TEN);
        WorkOrder wo = buildWorkOrder(1L, WorkOrderStatus.STARTED);
        wo.setOrderType(WorkOrderType.INSPECTION);
        when(operationMapper.selectByIdForUpdate(1L)).thenReturn(op);
        when(reportRecordMapper.sumReportedQuantityByOperationId(1L)).thenReturn(BigDecimal.ZERO);
        when(workOrderMapper.selectById(1L)).thenReturn(wo);

        Operation reportedOp = buildOperation(1L, 1L, "REPORTED", BigDecimal.TEN);
        reportedOp.setCompletedQuantity(BigDecimal.TEN);
        when(operationMapper.findByWorkOrderId(1L)).thenReturn(List.of(reportedOp));

        ReportRequest req = new ReportRequest();
        req.setOperationId(1L);
        req.setReportedQuantity(BigDecimal.TEN);

        reportService.reportWork(req, 10L, null);

        // INSPECTION order should go directly to COMPLETED, not REPORTED
        assertThat(wo.getStatus()).isEqualTo(WorkOrderStatus.COMPLETED);
    }

    @Test
    void testReportWork_TransportOrder_DirectlyCompletes() {
        Operation op = buildOperation(1L, 1L, "STARTED", BigDecimal.TEN);
        WorkOrder wo = buildWorkOrder(1L, WorkOrderStatus.STARTED);
        wo.setOrderType(WorkOrderType.TRANSPORT);
        when(operationMapper.selectByIdForUpdate(1L)).thenReturn(op);
        when(reportRecordMapper.sumReportedQuantityByOperationId(1L)).thenReturn(BigDecimal.ZERO);
        when(workOrderMapper.selectById(1L)).thenReturn(wo);

        Operation reportedOp = buildOperation(1L, 1L, "REPORTED", BigDecimal.TEN);
        reportedOp.setCompletedQuantity(BigDecimal.TEN);
        when(operationMapper.findByWorkOrderId(1L)).thenReturn(List.of(reportedOp));

        ReportRequest req = new ReportRequest();
        req.setOperationId(1L);
        req.setReportedQuantity(BigDecimal.TEN);

        reportService.reportWork(req, 10L, null);

        assertThat(wo.getStatus()).isEqualTo(WorkOrderStatus.COMPLETED);
    }

    @Test
    void testReportWork_ProductionOrder_GoesToReported() {
        Operation op = buildOperation(1L, 1L, "STARTED", BigDecimal.TEN);
        WorkOrder wo = buildWorkOrder(1L, WorkOrderStatus.STARTED);
        wo.setOrderType(WorkOrderType.PRODUCTION);
        when(operationMapper.selectByIdForUpdate(1L)).thenReturn(op);
        when(reportRecordMapper.sumReportedQuantityByOperationId(1L)).thenReturn(BigDecimal.ZERO);
        when(workOrderMapper.selectById(1L)).thenReturn(wo);

        Operation reportedOp = buildOperation(1L, 1L, "REPORTED", BigDecimal.TEN);
        reportedOp.setCompletedQuantity(BigDecimal.TEN);
        when(operationMapper.findByWorkOrderId(1L)).thenReturn(List.of(reportedOp));

        ReportRequest req = new ReportRequest();
        req.setOperationId(1L);
        req.setReportedQuantity(BigDecimal.TEN);

        reportService.reportWork(req, 10L, null);

        // PRODUCTION order goes to REPORTED (awaiting inspection)
        assertThat(wo.getStatus()).isEqualTo(WorkOrderStatus.REPORTED);
    }

    @Test
    void testReportWork_ForceStartDisabled_AllowsNotStarted() {
        // forceStartBeforeReport defaults to false via WorkOrderProperties mock
        Operation op = buildOperation(1L, 1L, "NOT_STARTED", BigDecimal.TEN);
        WorkOrder wo = buildWorkOrder(1L, WorkOrderStatus.NOT_STARTED);
        when(operationMapper.selectByIdForUpdate(1L)).thenReturn(op);
        when(reportRecordMapper.sumReportedQuantityByOperationId(1L)).thenReturn(BigDecimal.ZERO);
        when(workOrderMapper.selectById(1L)).thenReturn(wo);
        when(operationMapper.findByWorkOrderId(1L)).thenReturn(List.of(op));

        ReportRequest req = new ReportRequest();
        req.setOperationId(1L);
        req.setReportedQuantity(new BigDecimal("5"));

        // Should NOT throw: force-start is disabled so NOT_STARTED is allowed
        reportService.reportWork(req, 10L, null);
    }

    @Test
    void testStartWork_NoDependencies_StartsNormally() {
        Operation op = buildOperation(1L, 1L, "NOT_STARTED", BigDecimal.TEN);
        WorkOrder wo = buildWorkOrder(1L, WorkOrderStatus.NOT_STARTED);
        when(operationMapper.selectById(1L)).thenReturn(op);
        when(workOrderMapper.selectById(1L)).thenReturn(wo);
        // No dependencies → empty list (Mockito default for selectList)

        reportService.startWork(1L, 10L);

        assertThat(op.getStatus()).isEqualTo("STARTED");
    }

    @Test
    void testStartWork_PredecessorNotComplete_ThrowsException() {
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

        assertThatThrownBy(() -> reportService.startWork(2L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未完成");
    }

    @Test
    void testStartWork_ParallelDependency_NotBlocked() {
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
        reportService.startWork(2L, 10L);

        assertThat(op.getStatus()).isEqualTo("STARTED");
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
