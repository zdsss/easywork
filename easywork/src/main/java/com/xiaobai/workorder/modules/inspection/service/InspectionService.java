package com.xiaobai.workorder.modules.inspection.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiaobai.workorder.common.exception.BusinessException;
import com.xiaobai.workorder.common.enums.WorkOrderStatus;
import com.xiaobai.workorder.common.enums.WorkOrderType;
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

        boolean isInspectionOrder = WorkOrderType.INSPECTION == workOrder.getOrderType();
        boolean validStatus = WorkOrderStatus.REPORTED == workOrder.getStatus()
                || (isInspectionOrder && WorkOrderStatus.STARTED == workOrder.getStatus());
        if (!validStatus) {
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
        WorkOrderStatus previousStatus = workOrder.getStatus();
        String result = request.getInspectionResult();
        if ("PASSED".equals(result)) {
            workOrder.setStatus(WorkOrderStatus.INSPECT_PASSED);
        } else if ("CONCESSION".equals(result)) {
            // 让步接收：不合格品经评审按标准放行，状态同 INSPECT_PASSED，
            // 但 inspection_result 字段保留 "CONCESSION" 供审计追溯
            workOrder.setStatus(WorkOrderStatus.INSPECT_PASSED);
        } else if ("REWORK".equals(result) || "FAILED".equals(result)) {
            workOrder.setStatus(WorkOrderStatus.INSPECT_FAILED);
        } else if ("SCRAP_MATERIAL".equals(result) || "SCRAP_PROCESS".equals(result)) {
            workOrder.setStatus(WorkOrderStatus.SCRAPPED);
        } else {
            // Default: treat unknown results as failed
            workOrder.setStatus(WorkOrderStatus.INSPECT_FAILED);
        }
        workOrderMapper.updateById(workOrder);

        // Notify MES of the work order status transition
        eventPublisher.publishEvent(new WorkOrderStatusChangedEvent(
                this, workOrder.getId(), previousStatus.name(), workOrder.getStatus().name(), null));

        log.info("Inspection {} submitted for work order {} by inspector {}",
                request.getInspectionResult(), request.getWorkOrderId(), inspectorId);
        return record;
    }

    public InspectionRecord getLatestByWorkOrderId(Long workOrderId) {
        return inspectionRecordMapper.selectList(
                new LambdaQueryWrapper<InspectionRecord>()
                        .eq(InspectionRecord::getWorkOrderId, workOrderId)
                        .eq(InspectionRecord::getDeleted, 0)
                        .orderByDesc(InspectionRecord::getInspectionTime)
                        .last("LIMIT 1")
        ).stream().findFirst().orElseThrow(
                () -> new BusinessException("No inspection record found for work order: " + workOrderId)
        );
    }
}
