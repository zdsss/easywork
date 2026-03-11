package com.xiaobai.workorder.modules.mesintegration.service;

import com.xiaobai.workorder.modules.mesintegration.dto.MesImportResponse;
import com.xiaobai.workorder.modules.mesintegration.dto.MesWorkOrderImportRequest;
import com.xiaobai.workorder.modules.mesintegration.entity.MesOrderMapping;
import com.xiaobai.workorder.modules.mesintegration.entity.MesSyncLog;
import com.xiaobai.workorder.modules.mesintegration.repository.MesOrderMappingMapper;
import com.xiaobai.workorder.modules.operation.entity.Operation;
import com.xiaobai.workorder.modules.operation.entity.OperationAssignment;
import com.xiaobai.workorder.modules.operation.repository.OperationAssignmentMapper;
import com.xiaobai.workorder.modules.operation.repository.OperationMapper;
import com.xiaobai.workorder.modules.team.entity.Team;
import com.xiaobai.workorder.modules.team.repository.TeamMapper;
import com.xiaobai.workorder.modules.user.entity.User;
import com.xiaobai.workorder.modules.user.repository.UserMapper;
import com.xiaobai.workorder.modules.workorder.entity.WorkOrder;
import com.xiaobai.workorder.modules.workorder.repository.WorkOrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MesInboundServiceTest {

    @Mock WorkOrderMapper workOrderMapper;
    @Mock OperationMapper operationMapper;
    @Mock OperationAssignmentMapper assignmentMapper;
    @Mock MesOrderMappingMapper mappingMapper;
    @Mock UserMapper userMapper;
    @Mock TeamMapper teamMapper;
    @Mock MesSyncLogService syncLogService;

    @InjectMocks MesInboundService mesInboundService;

    @Test
    void importWorkOrder_newOrder_importsSuccessfully() {
        when(mappingMapper.findByMesOrderId("MES-001")).thenReturn(Optional.empty());
        MesSyncLog logEntry = new MesSyncLog();
        logEntry.setId(1L);
        when(syncLogService.createPending(any(), any(), any(), any())).thenReturn(logEntry);
        doAnswer(inv -> { ((WorkOrder) inv.getArgument(0)).setId(100L); return null; })
                .when(workOrderMapper).insert((WorkOrder) any(WorkOrder.class));

        MesWorkOrderImportRequest req = buildImportRequest("MES-001", "MES-WO-001");

        MesImportResponse response = mesInboundService.importWorkOrder(req);

        assertThat(response.getSyncStatus()).isEqualTo("SYNCED");
        assertThat(response.getMesOrderId()).isEqualTo("MES-001");
        verify(workOrderMapper).insert((WorkOrder) any(WorkOrder.class));
        verify(mappingMapper).insert((MesOrderMapping) any(MesOrderMapping.class));
        verify(syncLogService).markSuccess(eq(1L), any());
    }

    @Test
    void importWorkOrder_idempotent_skipsAlreadyImportedOrder() {
        MesOrderMapping existing = new MesOrderMapping();
        existing.setLocalOrderId(100L);
        existing.setLocalOrderNumber("MES-MES-WO-001");
        when(mappingMapper.findByMesOrderId("MES-001")).thenReturn(Optional.of(existing));

        MesWorkOrderImportRequest req = buildImportRequest("MES-001", "MES-WO-001");

        MesImportResponse response = mesInboundService.importWorkOrder(req);

        assertThat(response.getSyncStatus()).isEqualTo("SYNCED");
        assertThat(response.getLocalOrderId()).isEqualTo(100L);
        verify(workOrderMapper, never()).insert((WorkOrder) any(WorkOrder.class));
    }

    @Test
    void importWorkOrder_withEmployeeNumberAssignment_createsUserAssignment() {
        when(mappingMapper.findByMesOrderId("MES-002")).thenReturn(Optional.empty());
        MesSyncLog logEntry = new MesSyncLog();
        logEntry.setId(2L);
        when(syncLogService.createPending(any(), any(), any(), any())).thenReturn(logEntry);
        doAnswer(inv -> { ((WorkOrder) inv.getArgument(0)).setId(200L); return null; })
                .when(workOrderMapper).insert((WorkOrder) any(WorkOrder.class));
        doAnswer(inv -> { ((Operation) inv.getArgument(0)).setId(300L); return null; })
                .when(operationMapper).insert((Operation) any(Operation.class));

        User user = new User();
        user.setId(10L);
        user.setEmployeeNumber("EMP001");
        when(userMapper.findByEmployeeNumber("EMP001")).thenReturn(Optional.of(user));

        MesWorkOrderImportRequest req = buildImportRequest("MES-002", "MES-WO-002");
        MesWorkOrderImportRequest.MesOperationInput opInput = new MesWorkOrderImportRequest.MesOperationInput();
        opInput.setOperationName("Assembly");
        opInput.setAssignedEmployeeNumbers(List.of("EMP001"));
        req.setOperations(List.of(opInput));

        mesInboundService.importWorkOrder(req);

        ArgumentCaptor<OperationAssignment> captor = ArgumentCaptor.forClass(OperationAssignment.class);
        verify(assignmentMapper).insert((OperationAssignment) captor.capture());
        assertThat(captor.getValue().getAssignmentType()).isEqualTo("USER");
    }

    @Test
    void importWorkOrder_withTeamCodeAssignment_createsTeamAssignment() {
        when(mappingMapper.findByMesOrderId("MES-003")).thenReturn(Optional.empty());
        MesSyncLog logEntry = new MesSyncLog();
        logEntry.setId(3L);
        when(syncLogService.createPending(any(), any(), any(), any())).thenReturn(logEntry);
        doAnswer(inv -> { ((WorkOrder) inv.getArgument(0)).setId(300L); return null; })
                .when(workOrderMapper).insert((WorkOrder) any(WorkOrder.class));
        doAnswer(inv -> { ((Operation) inv.getArgument(0)).setId(400L); return null; })
                .when(operationMapper).insert((Operation) any(Operation.class));

        Team team = new Team();
        team.setId(5L);
        team.setTeamCode("TEAM-A");
        when(teamMapper.findByTeamCode("TEAM-A")).thenReturn(Optional.of(team));

        MesWorkOrderImportRequest req = buildImportRequest("MES-003", "MES-WO-003");
        MesWorkOrderImportRequest.MesOperationInput opInput = new MesWorkOrderImportRequest.MesOperationInput();
        opInput.setOperationName("Welding");
        opInput.setAssignedTeamCodes(List.of("TEAM-A"));
        req.setOperations(List.of(opInput));

        mesInboundService.importWorkOrder(req);

        ArgumentCaptor<OperationAssignment> captor = ArgumentCaptor.forClass(OperationAssignment.class);
        verify(assignmentMapper).insert((OperationAssignment) captor.capture());
        assertThat(captor.getValue().getAssignmentType()).isEqualTo("TEAM");
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private MesWorkOrderImportRequest buildImportRequest(String mesOrderId, String mesOrderNumber) {
        MesWorkOrderImportRequest req = new MesWorkOrderImportRequest();
        req.setMesOrderId(mesOrderId);
        req.setMesOrderNumber(mesOrderNumber);
        req.setOrderType("PRODUCTION");
        req.setProductCode("PROD-001");
        req.setProductName("Product One");
        req.setPlannedQuantity(BigDecimal.TEN);
        return req;
    }
}
