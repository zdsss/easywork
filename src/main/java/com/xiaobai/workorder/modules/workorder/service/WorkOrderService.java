package com.xiaobai.workorder.modules.workorder.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkOrderService {

    private final WorkOrderMapper workOrderMapper;
    private final OperationMapper operationMapper;
    private final OperationAssignmentMapper assignmentMapper;

    @Transactional
    public WorkOrderDTO createWorkOrder(CreateWorkOrderRequest request, Long createdBy) {
        if (workOrderMapper.findByOrderNumber(request.getOrderNumber()).isPresent()) {
            throw new BusinessException("Order number already exists: " + request.getOrderNumber());
        }

        WorkOrder workOrder = new WorkOrder();
        workOrder.setOrderNumber(request.getOrderNumber());
        workOrder.setOrderType(request.getOrderType());
        workOrder.setProductCode(request.getProductCode());
        workOrder.setProductName(request.getProductName());
        workOrder.setPlannedQuantity(request.getPlannedQuantity());
        workOrder.setCompletedQuantity(java.math.BigDecimal.ZERO);
        workOrder.setStatus("NOT_STARTED");
        workOrder.setPriority(request.getPriority() != null ? request.getPriority() : 0);
        workOrder.setPlannedStartTime(request.getPlannedStartTime());
        workOrder.setPlannedEndTime(request.getPlannedEndTime());
        workOrder.setWorkshop(request.getWorkshop());
        workOrder.setProductionLine(request.getProductionLine());
        workOrder.setNotes(request.getNotes());
        workOrder.setCreatedBy(createdBy);
        workOrderMapper.insert(workOrder);

        // Create operations
        if (request.getOperations() != null && !request.getOperations().isEmpty()) {
            AtomicInteger seq = new AtomicInteger(1);
            for (CreateWorkOrderRequest.OperationInput opInput : request.getOperations()) {
                Operation op = new Operation();
                op.setWorkOrderId(workOrder.getId());
                op.setOperationNumber(workOrder.getOrderNumber() + "-OP" +
                        String.format("%03d", seq.get()));
                op.setOperationName(opInput.getOperationName());
                op.setOperationType(opInput.getOperationType() != null
                        ? opInput.getOperationType() : "PRODUCTION");
                op.setSequenceNumber(opInput.getSequenceNumber() != null
                        ? opInput.getSequenceNumber() : seq.get());
                op.setPlannedQuantity(opInput.getPlannedQuantity() != null
                        ? opInput.getPlannedQuantity() : request.getPlannedQuantity());
                op.setCompletedQuantity(java.math.BigDecimal.ZERO);
                op.setStatus("NOT_STARTED");
                op.setStationCode(opInput.getStationCode());
                op.setStationName(opInput.getStationName());
                operationMapper.insert(op);
                seq.incrementAndGet();
            }
        }

        log.info("Work order {} created by user {}", workOrder.getOrderNumber(), createdBy);
        return toDTO(workOrder);
    }

    @Transactional
    public void assignWorkOrder(AssignWorkOrderRequest request) {
        Operation operation = operationMapper.selectById(request.getOperationId());
        if (operation == null || operation.getDeleted() == 1) {
            throw new BusinessException("Operation not found: " + request.getOperationId());
        }

        if ("USER".equals(request.getAssignmentType()) && request.getUserIds() != null) {
            for (Long userId : request.getUserIds()) {
                OperationAssignment assignment = new OperationAssignment();
                assignment.setOperationId(request.getOperationId());
                assignment.setAssignmentType("USER");
                assignment.setUserId(userId);
                assignment.setAssignedAt(LocalDateTime.now());
                assignmentMapper.insert(assignment);
            }
        } else if ("TEAM".equals(request.getAssignmentType()) && request.getTeamIds() != null) {
            for (Long teamId : request.getTeamIds()) {
                OperationAssignment assignment = new OperationAssignment();
                assignment.setOperationId(request.getOperationId());
                assignment.setAssignmentType("TEAM");
                assignment.setTeamId(teamId);
                assignment.setAssignedAt(LocalDateTime.now());
                assignmentMapper.insert(assignment);
            }
        }

        log.info("Operation {} assigned to {} {}", request.getOperationId(),
                request.getAssignmentType(), request.getUserIds() != null
                        ? request.getUserIds() : request.getTeamIds());
    }

    public List<WorkOrderDTO> getAssignedWorkOrders(Long userId) {
        List<WorkOrder> directOrders = workOrderMapper.findByDirectUserId(userId);
        List<WorkOrder> teamOrders = workOrderMapper.findByTeamMemberId(userId);

        List<WorkOrder> combined = new ArrayList<>(directOrders);
        teamOrders.stream()
                .filter(t -> directOrders.stream().noneMatch(d -> d.getId().equals(t.getId())))
                .forEach(combined::add);

        return combined.stream()
                .sorted((a, b) -> {
                    int pCmp = Integer.compare(b.getPriority(), a.getPriority());
                    if (pCmp != 0) return pCmp;
                    if (a.getPlannedStartTime() != null && b.getPlannedStartTime() != null) {
                        return a.getPlannedStartTime().compareTo(b.getPlannedStartTime());
                    }
                    return 0;
                })
                .map(this::toDTO)
                .toList();
    }

    public WorkOrderDTO getWorkOrderById(Long id) {
        WorkOrder workOrder = workOrderMapper.selectById(id);
        if (workOrder == null || workOrder.getDeleted() == 1) {
            throw new BusinessException("Work order not found: " + id);
        }
        return toDTO(workOrder);
    }

    public List<WorkOrderDTO> listAllWorkOrders(int page, int size, String status) {
        LambdaQueryWrapper<WorkOrder> wrapper = new LambdaQueryWrapper<WorkOrder>()
                .eq(WorkOrder::getDeleted, 0)
                .orderByDesc(WorkOrder::getPriority)
                .orderByDesc(WorkOrder::getCreatedAt);
        if (status != null && !status.isBlank()) {
            wrapper.eq(WorkOrder::getStatus, status);
        }
        Page<WorkOrder> pageResult = workOrderMapper.selectPage(new Page<>(page, size), wrapper);
        return pageResult.getRecords().stream().map(this::toDTO).toList();
    }

    public WorkOrderDTO getWorkOrderByBarcode(String barcode, Long userId) {
        WorkOrder workOrder = workOrderMapper.findByOrderNumber(barcode)
                .orElseThrow(() -> new BusinessException("No work order found for barcode: " + barcode));
        return toDTO(workOrder);
    }

    private WorkOrderDTO toDTO(WorkOrder workOrder) {
        WorkOrderDTO dto = new WorkOrderDTO();
        dto.setId(workOrder.getId());
        dto.setOrderNumber(workOrder.getOrderNumber());
        dto.setOrderType(workOrder.getOrderType());
        dto.setProductCode(workOrder.getProductCode());
        dto.setProductName(workOrder.getProductName());
        dto.setPlannedQuantity(workOrder.getPlannedQuantity());
        dto.setCompletedQuantity(workOrder.getCompletedQuantity());
        if (workOrder.getPlannedQuantity() != null && workOrder.getCompletedQuantity() != null) {
            dto.setRemainingQuantity(workOrder.getPlannedQuantity()
                    .subtract(workOrder.getCompletedQuantity()));
        }
        dto.setStatus(workOrder.getStatus());
        dto.setPriority(workOrder.getPriority());
        dto.setPlannedStartTime(workOrder.getPlannedStartTime());
        dto.setPlannedEndTime(workOrder.getPlannedEndTime());
        dto.setActualStartTime(workOrder.getActualStartTime());
        dto.setWorkshop(workOrder.getWorkshop());
        dto.setProductionLine(workOrder.getProductionLine());
        dto.setNotes(workOrder.getNotes());

        List<Operation> operations = operationMapper.findByWorkOrderId(workOrder.getId());
        dto.setOperations(operations.stream().map(op -> {
            WorkOrderDTO.OperationSummary summary = new WorkOrderDTO.OperationSummary();
            summary.setId(op.getId());
            summary.setOperationNumber(op.getOperationNumber());
            summary.setOperationName(op.getOperationName());
            summary.setOperationType(op.getOperationType());
            summary.setSequenceNumber(op.getSequenceNumber());
            summary.setPlannedQuantity(op.getPlannedQuantity());
            summary.setCompletedQuantity(op.getCompletedQuantity());
            summary.setStatus(op.getStatus());
            summary.setStationCode(op.getStationCode());
            summary.setStationName(op.getStationName());
            return summary;
        }).toList());

        return dto;
    }
}
