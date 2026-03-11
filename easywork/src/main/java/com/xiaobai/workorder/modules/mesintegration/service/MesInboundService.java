package com.xiaobai.workorder.modules.mesintegration.service;

import com.xiaobai.workorder.common.exception.BusinessException;
import com.xiaobai.workorder.modules.mesintegration.constant.MesSyncDirection;
import com.xiaobai.workorder.modules.mesintegration.constant.MesSyncStatus;
import com.xiaobai.workorder.modules.mesintegration.constant.MesSyncType;
import com.xiaobai.workorder.modules.mesintegration.dto.MesImportResponse;
import com.xiaobai.workorder.modules.mesintegration.dto.MesWorkOrderImportRequest;
import com.xiaobai.workorder.modules.mesintegration.entity.MesOrderMapping;
import com.xiaobai.workorder.modules.mesintegration.entity.MesSyncLog;
import com.xiaobai.workorder.modules.mesintegration.repository.MesOrderMappingMapper;
import com.xiaobai.workorder.modules.operation.entity.Operation;
import com.xiaobai.workorder.modules.operation.entity.OperationAssignment;
import com.xiaobai.workorder.modules.operation.repository.OperationAssignmentMapper;
import com.xiaobai.workorder.modules.operation.repository.OperationMapper;
import com.xiaobai.workorder.modules.team.repository.TeamMapper;
import com.xiaobai.workorder.modules.user.repository.UserMapper;
import com.xiaobai.workorder.modules.workorder.entity.WorkOrder;
import com.xiaobai.workorder.modules.workorder.repository.WorkOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles inbound work order imports received from MES via webhook.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MesInboundService {

    private final WorkOrderMapper workOrderMapper;
    private final OperationMapper operationMapper;
    private final OperationAssignmentMapper assignmentMapper;
    private final MesOrderMappingMapper mappingMapper;
    private final UserMapper userMapper;
    private final TeamMapper teamMapper;
    private final MesSyncLogService syncLogService;

    /**
     * Import (or update) a work order received from MES.
     * Idempotent: if the mesOrderId already exists, skips re-import.
     */
    @Transactional
    public MesImportResponse importWorkOrder(MesWorkOrderImportRequest request) {
        // Idempotency check
        if (request.getMesOrderId() != null) {
            MesOrderMapping existing = mappingMapper
                    .findByMesOrderId(request.getMesOrderId()).orElse(null);
            if (existing != null) {
                log.info("MES order {} already imported as local order {}",
                        request.getMesOrderId(), existing.getLocalOrderNumber());
                return MesImportResponse.success(
                        request.getMesOrderId(), request.getMesOrderNumber(),
                        existing.getLocalOrderId(), existing.getLocalOrderNumber());
            }
        }

        MesSyncLog logEntry = syncLogService.createPending(
                MesSyncType.WORK_ORDER_IMPORT, MesSyncDirection.INBOUND,
                request.getMesOrderNumber(), request);

        try {
            // Build work order
            WorkOrder workOrder = buildWorkOrder(request);
            workOrderMapper.insert(workOrder);

            // Build operations with assignments
            if (request.getOperations() != null) {
                AtomicInteger seq = new AtomicInteger(1);
                for (MesWorkOrderImportRequest.MesOperationInput opInput : request.getOperations()) {
                    Operation op = buildOperation(workOrder, opInput, seq.getAndIncrement());
                    operationMapper.insert(op);
                    createAssignments(op.getId(), opInput);
                }
            }

            // Record mapping
            MesOrderMapping mapping = new MesOrderMapping();
            mapping.setLocalOrderId(workOrder.getId());
            mapping.setLocalOrderNumber(workOrder.getOrderNumber());
            mapping.setMesOrderId(request.getMesOrderId());
            mapping.setMesOrderNumber(request.getMesOrderNumber());
            mapping.setSyncStatus(MesSyncStatus.SUCCESS);
            mapping.setLastSyncedAt(LocalDateTime.now());
            mappingMapper.insert(mapping);

            syncLogService.markSuccess(logEntry.getId(), "Imported as " + workOrder.getOrderNumber());
            log.info("MES order {} imported successfully as local order {}",
                    request.getMesOrderNumber(), workOrder.getOrderNumber());

            return MesImportResponse.success(request.getMesOrderId(),
                    request.getMesOrderNumber(), workOrder.getId(), workOrder.getOrderNumber());

        } catch (Exception e) {
            syncLogService.markFailed(logEntry.getId(), e.getMessage());
            log.error("Failed to import MES order {}: {}", request.getMesOrderNumber(), e.getMessage());
            throw new BusinessException("MES work order import failed: " + e.getMessage());
        }
    }

    private WorkOrder buildWorkOrder(MesWorkOrderImportRequest req) {
        // Use MES order number if provided; otherwise generate one
        String orderNumber = req.getMesOrderNumber() != null && !req.getMesOrderNumber().isBlank()
                ? "MES-" + req.getMesOrderNumber()
                : "MES-" + System.currentTimeMillis();

        WorkOrder wo = new WorkOrder();
        wo.setOrderNumber(orderNumber);
        wo.setOrderType(req.getOrderType() != null ? req.getOrderType() : "PRODUCTION");
        wo.setProductCode(req.getProductCode());
        wo.setProductName(req.getProductName());
        wo.setPlannedQuantity(req.getPlannedQuantity() != null
                ? req.getPlannedQuantity() : BigDecimal.ONE);
        wo.setCompletedQuantity(BigDecimal.ZERO);
        wo.setStatus("NOT_STARTED");
        wo.setPriority(req.getPriority() != null ? req.getPriority() : 0);
        wo.setPlannedStartTime(req.getPlannedStartTime());
        wo.setPlannedEndTime(req.getPlannedEndTime());
        wo.setWorkshop(req.getWorkshop());
        wo.setProductionLine(req.getProductionLine());
        wo.setNotes(req.getNotes());
        return wo;
    }

    private Operation buildOperation(WorkOrder workOrder,
                                      MesWorkOrderImportRequest.MesOperationInput input,
                                      int seq) {
        Operation op = new Operation();
        op.setWorkOrderId(workOrder.getId());
        op.setOperationNumber(workOrder.getOrderNumber() + "-OP" + String.format("%03d", seq));
        op.setOperationName(input.getOperationName());
        op.setOperationType(input.getOperationType() != null ? input.getOperationType() : "PRODUCTION");
        op.setSequenceNumber(input.getSequenceNumber() != null ? input.getSequenceNumber() : seq);
        op.setPlannedQuantity(input.getPlannedQuantity() != null
                ? input.getPlannedQuantity() : workOrder.getPlannedQuantity());
        op.setCompletedQuantity(BigDecimal.ZERO);
        op.setStatus("NOT_STARTED");
        op.setStationCode(input.getStationCode());
        op.setStationName(input.getStationName());
        return op;
    }

    private void createAssignments(Long operationId,
                                    MesWorkOrderImportRequest.MesOperationInput input) {
        // Assign by employee number
        if (input.getAssignedEmployeeNumbers() != null) {
            for (String empNo : input.getAssignedEmployeeNumbers()) {
                userMapper.findByEmployeeNumber(empNo).ifPresent(user -> {
                    OperationAssignment a = new OperationAssignment();
                    a.setOperationId(operationId);
                    a.setAssignmentType("USER");
                    a.setUserId(user.getId());
                    a.setAssignedAt(LocalDateTime.now());
                    assignmentMapper.insert(a);
                });
            }
        }
        // Assign by team code
        if (input.getAssignedTeamCodes() != null) {
            for (String teamCode : input.getAssignedTeamCodes()) {
                teamMapper.findByTeamCode(teamCode).ifPresent(team -> {
                    OperationAssignment a = new OperationAssignment();
                    a.setOperationId(operationId);
                    a.setAssignmentType("TEAM");
                    a.setTeamId(team.getId());
                    a.setAssignedAt(LocalDateTime.now());
                    assignmentMapper.insert(a);
                });
            }
        }
    }
}
