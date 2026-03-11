package com.xiaobai.workorder.modules.workorder.service;

import com.xiaobai.workorder.common.exception.BusinessException;
import com.xiaobai.workorder.modules.operation.entity.Operation;
import com.xiaobai.workorder.modules.operation.entity.OperationAssignment;
import com.xiaobai.workorder.modules.operation.repository.OperationAssignmentMapper;
import com.xiaobai.workorder.modules.operation.repository.OperationMapper;
import com.xiaobai.workorder.modules.workorder.dto.AssignWorkOrderRequest;
import com.xiaobai.workorder.modules.workorder.dto.CreateWorkOrderRequest;
import com.xiaobai.workorder.modules.workorder.dto.WorkOrderDTO;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkOrderServiceTest {

    @Mock WorkOrderMapper workOrderMapper;
    @Mock OperationMapper operationMapper;
    @Mock OperationAssignmentMapper assignmentMapper;

    @InjectMocks WorkOrderService workOrderService;

    @Test
    void createWorkOrder_newOrder_insertsWorkOrderAndReturnsDTO() {
        when(workOrderMapper.findByOrderNumber("WO-001")).thenReturn(Optional.empty());
        doAnswer(inv -> {
            WorkOrder wo = inv.getArgument(0);
            wo.setId(1L);
            return null;
        }).when(workOrderMapper).insert((WorkOrder) any(WorkOrder.class));
        when(operationMapper.findByWorkOrderId(1L)).thenReturn(List.of());

        CreateWorkOrderRequest req = buildCreateRequest("WO-001");

        WorkOrderDTO dto = workOrderService.createWorkOrder(req, 1L);

        assertThat(dto.getOrderNumber()).isEqualTo("WO-001");
        assertThat(dto.getStatus()).isEqualTo("NOT_STARTED");
        verify(workOrderMapper).insert((WorkOrder) any(WorkOrder.class));
    }

    @Test
    void createWorkOrder_duplicateOrderNumber_throwsBusinessException() {
        WorkOrder existing = new WorkOrder();
        existing.setOrderNumber("WO-001");
        when(workOrderMapper.findByOrderNumber("WO-001")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> workOrderService.createWorkOrder(buildCreateRequest("WO-001"), 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createWorkOrder_withOperations_generatesOperationNumbers() {
        when(workOrderMapper.findByOrderNumber("WO-001")).thenReturn(Optional.empty());
        doAnswer(inv -> { ((WorkOrder) inv.getArgument(0)).setId(1L); return null; })
                .when(workOrderMapper).insert((WorkOrder) any(WorkOrder.class));
        when(operationMapper.findByWorkOrderId(1L)).thenReturn(List.of());

        CreateWorkOrderRequest req = buildCreateRequest("WO-001");
        CreateWorkOrderRequest.OperationInput op1 = new CreateWorkOrderRequest.OperationInput();
        op1.setOperationName("Cutting");
        CreateWorkOrderRequest.OperationInput op2 = new CreateWorkOrderRequest.OperationInput();
        op2.setOperationName("Welding");
        CreateWorkOrderRequest.OperationInput op3 = new CreateWorkOrderRequest.OperationInput();
        op3.setOperationName("Painting");
        req.setOperations(List.of(op1, op2, op3));

        workOrderService.createWorkOrder(req, 1L);

        ArgumentCaptor<Operation> captor = ArgumentCaptor.forClass(Operation.class);
        verify(operationMapper, times(3)).insert((Operation) captor.capture());
        List<Operation> ops = captor.getAllValues();
        assertThat(ops.get(0).getOperationNumber()).isEqualTo("WO-001-OP001");
        assertThat(ops.get(1).getOperationNumber()).isEqualTo("WO-001-OP002");
        assertThat(ops.get(2).getOperationNumber()).isEqualTo("WO-001-OP003");
    }

    @Test
    void getWorkOrderById_existingId_returnsDTO() {
        WorkOrder wo = buildWorkOrder(1L, "NOT_STARTED");
        when(workOrderMapper.selectById(1L)).thenReturn(wo);
        when(operationMapper.findByWorkOrderId(1L)).thenReturn(List.of());

        WorkOrderDTO dto = workOrderService.getWorkOrderById(1L);

        assertThat(dto.getId()).isEqualTo(1L);
    }

    @Test
    void getWorkOrderById_notFound_throwsBusinessException() {
        when(workOrderMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> workOrderService.getWorkOrderById(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Work order not found");
    }

    @Test
    void getAssignedWorkOrders_deduplicatesMergesDirectAndTeamOrders() {
        WorkOrder wo1 = buildWorkOrder(1L, "NOT_STARTED");
        WorkOrder wo2 = buildWorkOrder(2L, "STARTED");
        // wo1 appears in both results - should be deduplicated
        when(workOrderMapper.findByDirectUserId(10L)).thenReturn(List.of(wo1));
        when(workOrderMapper.findByTeamMemberId(10L)).thenReturn(List.of(wo1, wo2));
        when(operationMapper.findByWorkOrderId(anyLong())).thenReturn(List.of());

        List<WorkOrderDTO> result = workOrderService.getAssignedWorkOrders(10L);

        assertThat(result).hasSize(2);
    }

    @Test
    void getAssignedWorkOrders_sortsByPriorityDesc() {
        WorkOrder lowPriority = buildWorkOrder(1L, "NOT_STARTED");
        lowPriority.setPriority(1);
        WorkOrder highPriority = buildWorkOrder(2L, "NOT_STARTED");
        highPriority.setPriority(5);

        when(workOrderMapper.findByDirectUserId(10L)).thenReturn(List.of(lowPriority, highPriority));
        when(workOrderMapper.findByTeamMemberId(10L)).thenReturn(List.of());
        when(operationMapper.findByWorkOrderId(anyLong())).thenReturn(List.of());

        List<WorkOrderDTO> result = workOrderService.getAssignedWorkOrders(10L);

        assertThat(result.get(0).getId()).isEqualTo(2L); // high priority first
    }

    @Test
    void assignWorkOrder_userType_insertsAssignments() {
        Operation op = new Operation();
        op.setId(1L);
        op.setDeleted(0);
        when(operationMapper.selectById(1L)).thenReturn(op);

        AssignWorkOrderRequest req = new AssignWorkOrderRequest();
        req.setOperationId(1L);
        req.setAssignmentType("USER");
        req.setUserIds(List.of(10L, 11L));

        workOrderService.assignWorkOrder(req);

        verify(assignmentMapper, times(2)).insert((OperationAssignment) any(OperationAssignment.class));
    }

    @Test
    void assignWorkOrder_teamType_insertsTeamAssignments() {
        Operation op = new Operation();
        op.setId(1L);
        op.setDeleted(0);
        when(operationMapper.selectById(1L)).thenReturn(op);

        AssignWorkOrderRequest req = new AssignWorkOrderRequest();
        req.setOperationId(1L);
        req.setAssignmentType("TEAM");
        req.setTeamIds(List.of(5L));

        workOrderService.assignWorkOrder(req);

        ArgumentCaptor<OperationAssignment> captor = ArgumentCaptor.forClass(OperationAssignment.class);
        verify(assignmentMapper).insert((OperationAssignment) captor.capture());
        assertThat(captor.getValue().getAssignmentType()).isEqualTo("TEAM");
    }

    @Test
    void assignWorkOrder_operationNotFound_throwsBusinessException() {
        when(operationMapper.selectById(99L)).thenReturn(null);

        AssignWorkOrderRequest req = new AssignWorkOrderRequest();
        req.setOperationId(99L);
        req.setAssignmentType("USER");

        assertThatThrownBy(() -> workOrderService.assignWorkOrder(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Operation not found");
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private CreateWorkOrderRequest buildCreateRequest(String orderNumber) {
        CreateWorkOrderRequest req = new CreateWorkOrderRequest();
        req.setOrderNumber(orderNumber);
        req.setOrderType("PRODUCTION");
        req.setPlannedQuantity(BigDecimal.TEN);
        return req;
    }

    private WorkOrder buildWorkOrder(Long id, String status) {
        WorkOrder wo = new WorkOrder();
        wo.setId(id);
        wo.setOrderNumber("WO-00" + id);
        wo.setOrderType("PRODUCTION");
        wo.setStatus(status);
        wo.setPlannedQuantity(BigDecimal.TEN);
        wo.setCompletedQuantity(BigDecimal.ZERO);
        wo.setPriority(0);
        wo.setDeleted(0);
        return wo;
    }
}
