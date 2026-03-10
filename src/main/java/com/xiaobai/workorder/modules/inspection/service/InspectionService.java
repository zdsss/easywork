package com.xiaobai.workorder.modules.inspection.service;

import com.xiaobai.workorder.common.exception.BusinessException;
import com.xiaobai.workorder.modules.inspection.dto.InspectionRequest;
import com.xiaobai.workorder.modules.inspection.entity.InspectionRecord;
import com.xiaobai.workorder.modules.inspection.repository.InspectionRecordMapper;
import com.xiaobai.workorder.modules.mesintegration.event.InspectionRecordSavedEvent;
import com.xiaobai.workorder.modules.mesintegration.event.WorkOrderStatusChangedEvent;
import com.xiaobai.workorder.modules.workorder.entity.WorkOrder;
import com.xiaobai.workorder.modules.workorder.repository.WorkOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class InspectionService {

    private final InspectionRecordMapper inspectionRecordMapper;
    private final WorkOrderMapper workOrderMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public InspectionRecord submitInspection(InspectionRequest request, Long inspectorId) {
        WorkOrder workOrder = workOrderMapper.selectById(request.getWorkOrderId());
        if (workOrder == null || workOrder.getDeleted() == 1) {
            throw new BusinessException("Work order not found: " + request.getWorkOrderId());
        }

        if (!"REPORTED".equals(workOrder.getStatus())) {
            throw new BusinessException(
                    "Work order must be in REPORTED status to inspect, current: " + workOrder.getStatus());
        }

        InspectionRecord record = new InspectionRecord();
        record.setWorkOrderId(request.getWorkOrderId());
        record.setOperationId(request.getOperationId());
        record.setInspectorId(inspectorId);
        record.setInspectionType("QUALITY");
        record.setInspectionResult(request.getInspectionResult());
        record.setInspectedQuantity(request.getInspectedQuantity());
        record.setQualifiedQuantity(request.getQualifiedQuantity());
        record.setDefectQuantity(request.getDefectQuantity());
        record.setDefectReason(request.getDefectReason());
        record.setStatus("INSPECTED");
        record.setInspectionTime(LocalDateTime.now());
        record.setNotes(request.getNotes());
        inspectionRecordMapper.insert(record);

        // Publish inspection event for MES push (fires after transaction commits)
        eventPublisher.publishEvent(
                new InspectionRecordSavedEvent(this, record.getId(), record));

        // Update work order status based on result
        String previousStatus = workOrder.getStatus();
        if ("PASSED".equals(request.getInspectionResult())) {
            workOrder.setStatus("INSPECT_PASSED");
        } else {
            workOrder.setStatus("INSPECT_FAILED");
        }
        workOrderMapper.updateById(workOrder);

        // Notify MES of the work order status transition
        eventPublisher.publishEvent(new WorkOrderStatusChangedEvent(
                this, workOrder.getId(), previousStatus, workOrder.getStatus(), null));

        log.info("Inspection {} submitted for work order {} by inspector {}",
                request.getInspectionResult(), request.getWorkOrderId(), inspectorId);
        return record;
    }
}
