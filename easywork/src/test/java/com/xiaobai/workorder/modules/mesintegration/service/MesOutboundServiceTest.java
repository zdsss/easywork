package com.xiaobai.workorder.modules.mesintegration.service;

import com.xiaobai.workorder.modules.mesintegration.client.MesApiClient;
import com.xiaobai.workorder.modules.mesintegration.entity.MesSyncLog;
import com.xiaobai.workorder.modules.mesintegration.repository.MesOrderMappingMapper;
import com.xiaobai.workorder.modules.operation.entity.Operation;
import com.xiaobai.workorder.modules.operation.repository.OperationMapper;
import com.xiaobai.workorder.modules.report.entity.ReportRecord;
import com.xiaobai.workorder.modules.report.repository.ReportRecordMapper;
import com.xiaobai.workorder.modules.user.entity.User;
import com.xiaobai.workorder.modules.user.repository.UserMapper;
import com.xiaobai.workorder.modules.workorder.entity.WorkOrder;
import com.xiaobai.workorder.modules.workorder.repository.WorkOrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MesOutboundServiceTest {

    @Mock MesSyncLogService syncLogService;
    @Mock MesOrderMappingMapper mappingMapper;
    @Mock WorkOrderMapper workOrderMapper;
    @Mock OperationMapper operationMapper;
    @Mock ReportRecordMapper reportRecordMapper;
    @Mock UserMapper userMapper;
    @Mock ObjectProvider<MesApiClient> mesApiClientProvider;

    @InjectMocks MesOutboundService mesOutboundService;

    @Test
    void pushReport_integrationEnabled_callsMesApiAndMarksSuccess() {
        MesApiClient client = mock(MesApiClient.class);
        when(mesApiClientProvider.getIfAvailable()).thenReturn(client);
        when(client.pushReport(any())).thenReturn("OK");

        MesSyncLog logEntry = new MesSyncLog();
        logEntry.setId(1L);
        when(syncLogService.createPending(any(), any(), any(), any())).thenReturn(logEntry);

        ReportRecord record = buildReportRecord(1L, 1L, 1L);
        when(reportRecordMapper.selectById(1L)).thenReturn(record);
        when(workOrderMapper.selectById(1L)).thenReturn(buildWorkOrder(1L));
        when(operationMapper.selectById(1L)).thenReturn(buildOperation(1L));
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L));
        when(mappingMapper.findByLocalOrderId(1L)).thenReturn(Optional.empty());

        mesOutboundService.pushReport(1L);

        verify(client).pushReport(any());
        verify(syncLogService).markSuccess(eq(1L), any());
    }

    @Test
    void pushReport_integrationDisabled_doesNotCallMesApi() {
        when(mesApiClientProvider.getIfAvailable()).thenReturn(null);

        mesOutboundService.pushReport(1L);

        verify(reportRecordMapper, never()).selectById(any());
        verify(syncLogService, never()).createPending(any(), any(), any(), any());
    }

    @Test
    void pushReport_mesApiThrowsException_marksLogFailed() {
        MesApiClient client = mock(MesApiClient.class);
        when(mesApiClientProvider.getIfAvailable()).thenReturn(client);
        when(client.pushReport(any())).thenThrow(new RuntimeException("Connection refused"));

        MesSyncLog logEntry = new MesSyncLog();
        logEntry.setId(2L);
        when(syncLogService.createPending(any(), any(), any(), any())).thenReturn(logEntry);

        ReportRecord record = buildReportRecord(1L, 1L, 1L);
        when(reportRecordMapper.selectById(1L)).thenReturn(record);
        when(workOrderMapper.selectById(1L)).thenReturn(buildWorkOrder(1L));
        when(operationMapper.selectById(1L)).thenReturn(buildOperation(1L));
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L));
        when(mappingMapper.findByLocalOrderId(1L)).thenReturn(Optional.empty());

        // Should NOT throw - exception is swallowed and logged
        mesOutboundService.pushReport(1L);

        verify(syncLogService).markFailed(eq(2L), any());
    }

    @Test
    void pushReport_createsLogBeforeCallingApi() {
        MesApiClient client = mock(MesApiClient.class);
        when(mesApiClientProvider.getIfAvailable()).thenReturn(client);
        when(client.pushReport(any())).thenReturn("OK");

        MesSyncLog logEntry = new MesSyncLog();
        logEntry.setId(3L);
        when(syncLogService.createPending(any(), any(), any(), any())).thenReturn(logEntry);

        ReportRecord record = buildReportRecord(1L, 1L, 1L);
        when(reportRecordMapper.selectById(1L)).thenReturn(record);
        when(workOrderMapper.selectById(1L)).thenReturn(buildWorkOrder(1L));
        when(operationMapper.selectById(1L)).thenReturn(buildOperation(1L));
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L));
        when(mappingMapper.findByLocalOrderId(1L)).thenReturn(Optional.empty());

        mesOutboundService.pushReport(1L);

        // Verify that createPending was called BEFORE pushReport
        var inOrder = inOrder(syncLogService, client);
        inOrder.verify(syncLogService).createPending(any(), any(), any(), any());
        inOrder.verify(client).pushReport(any());
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private ReportRecord buildReportRecord(Long id, Long workOrderId, Long operationId) {
        ReportRecord r = new ReportRecord();
        r.setId(id);
        r.setWorkOrderId(workOrderId);
        r.setOperationId(operationId);
        r.setUserId(1L);
        r.setReportedQuantity(BigDecimal.TEN);
        r.setQualifiedQuantity(BigDecimal.TEN);
        r.setDefectQuantity(BigDecimal.ZERO);
        r.setReportTime(LocalDateTime.now());
        r.setIsUndone(false);
        return r;
    }

    private WorkOrder buildWorkOrder(Long id) {
        WorkOrder wo = new WorkOrder();
        wo.setId(id);
        wo.setOrderNumber("WO-001");
        return wo;
    }

    private Operation buildOperation(Long id) {
        Operation op = new Operation();
        op.setId(id);
        op.setOperationNumber("WO-001-OP001");
        op.setOperationName("Test Op");
        return op;
    }

    private User buildUser(Long id) {
        User user = new User();
        user.setId(id);
        user.setEmployeeNumber("EMP001");
        user.setRealName("Test User");
        return user;
    }
}
