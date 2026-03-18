package com.xiaobai.workorder.modules.device.service;

import com.xiaobai.workorder.common.enums.OperationStatus;
import com.xiaobai.workorder.modules.operation.entity.Operation;
import com.xiaobai.workorder.modules.operation.repository.OperationMapper;
import com.xiaobai.workorder.modules.workorder.dto.WorkOrderDTO;
import com.xiaobai.workorder.modules.workorder.service.WorkOrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScanServiceTest {

    @Mock OperationMapper operationMapper;
    @Mock WorkOrderService workOrderService;

    @InjectMocks ScanService scanService;

    // ---------------------------------------------------------------
    // resolveScanStart tests
    // ---------------------------------------------------------------

    @Test
    void resolveScanStart_withOperationBarcode_findsOperationDirectly() {
        Operation op = buildOperation(1L, 10L, "OP-001", OperationStatus.NOT_STARTED);
        when(operationMapper.findByOperationNumber("OP-001")).thenReturn(Optional.of(op));

        ScanService.ScanStartResult result = scanService.resolveScanStart("OP-001", 5L);

        assertThat(result.workOrderId()).isEqualTo(10L);
        assertThat(result.operation()).isSameAs(op);
        verify(workOrderService, never()).getWorkOrderByBarcode(any(), any());
    }

    @Test
    void resolveScanStart_withWorkOrderBarcode_fallsBackToWorkOrderResolution() {
        when(operationMapper.findByOperationNumber("WO-100")).thenReturn(Optional.empty());
        WorkOrderDTO dto = new WorkOrderDTO();
        dto.setId(20L);
        when(workOrderService.getWorkOrderByBarcode("WO-100", 5L)).thenReturn(dto);
        Operation op = buildOperation(2L, 20L, "OP-002", OperationStatus.NOT_STARTED);
        when(operationMapper.findEarliestNotStartedByUserAndWorkOrder(5L, 20L)).thenReturn(op);

        ScanService.ScanStartResult result = scanService.resolveScanStart("WO-100", 5L);

        assertThat(result.workOrderId()).isEqualTo(20L);
        assertThat(result.operation()).isSameAs(op);
    }

    @Test
    void resolveScanStart_withNotStartedOperation_returnsIt() {
        Operation op = buildOperation(3L, 30L, "OP-003", OperationStatus.NOT_STARTED);
        when(operationMapper.findByOperationNumber("OP-003")).thenReturn(Optional.of(op));

        ScanService.ScanStartResult result = scanService.resolveScanStart("OP-003", 5L);

        assertThat(result.operation()).isNotNull();
        assertThat(result.operation().getStatus()).isEqualTo(OperationStatus.NOT_STARTED);
    }

    @Test
    void resolveScanStart_withAlreadyStartedOperation_returnsNullOperation() {
        Operation op = buildOperation(4L, 40L, "OP-004", OperationStatus.STARTED);
        when(operationMapper.findByOperationNumber("OP-004")).thenReturn(Optional.of(op));

        ScanService.ScanStartResult result = scanService.resolveScanStart("OP-004", 5L);

        assertThat(result.workOrderId()).isEqualTo(40L);
        assertThat(result.operation()).isNull(); // no-op start
    }

    // ---------------------------------------------------------------
    // resolveScanReport tests
    // ---------------------------------------------------------------

    @Test
    void resolveScanReport_withOperationBarcode_findsOperation() {
        Operation op = buildOperation(5L, 50L, "OP-005", OperationStatus.STARTED);
        when(operationMapper.findByOperationNumber("OP-005")).thenReturn(Optional.of(op));

        ScanService.ScanReportResult result = scanService.resolveScanReport("OP-005", 5L);

        assertThat(result.workOrderId()).isEqualTo(50L);
        assertThat(result.operation()).isSameAs(op);
    }

    @Test
    void resolveScanReport_withUnknownBarcode_returnsScanReportResultWithNullOperation() {
        when(operationMapper.findByOperationNumber("UNKNOWN")).thenReturn(Optional.empty());
        WorkOrderDTO dto = new WorkOrderDTO();
        dto.setId(60L);
        when(workOrderService.getWorkOrderByBarcode("UNKNOWN", 5L)).thenReturn(dto);
        when(operationMapper.findEarliestUnfinishedByUserAndWorkOrder(5L, 60L)).thenReturn(null);
        when(operationMapper.findEarliestUnfinishedByTeamUserAndWorkOrder(5L, 60L)).thenReturn(null);

        ScanService.ScanReportResult result = scanService.resolveScanReport("UNKNOWN", 5L);

        assertThat(result.workOrderId()).isEqualTo(60L);
        assertThat(result.operation()).isNull();
    }

    @Test
    void resolveScanReport_withCompletedOperationBarcode_fallsThroughToNextAvailable() {
        Operation completedOp = buildOperation(6L, 70L, "OP-006", OperationStatus.COMPLETED);
        when(operationMapper.findByOperationNumber("OP-006")).thenReturn(Optional.of(completedOp));
        Operation nextOp = buildOperation(7L, 70L, "OP-007", OperationStatus.STARTED);
        when(operationMapper.findEarliestUnfinishedByUserAndWorkOrder(5L, 70L)).thenReturn(nextOp);

        ScanService.ScanReportResult result = scanService.resolveScanReport("OP-006", 5L);

        assertThat(result.workOrderId()).isEqualTo(70L);
        assertThat(result.operation()).isSameAs(nextOp);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private Operation buildOperation(Long id, Long workOrderId, String operationNumber, OperationStatus status) {
        Operation op = new Operation();
        op.setId(id);
        op.setWorkOrderId(workOrderId);
        op.setOperationNumber(operationNumber);
        op.setStatus(status);
        op.setDeleted(0);
        return op;
    }
}
