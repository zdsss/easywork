package com.xiaobai.workorder.modules.mesintegration.service;

import com.xiaobai.workorder.common.exception.BusinessException;
import com.xiaobai.workorder.modules.mesintegration.client.MesApiClient;
import com.xiaobai.workorder.modules.mesintegration.constant.MesSyncDirection;
import com.xiaobai.workorder.modules.mesintegration.constant.MesSyncType;
import com.xiaobai.workorder.modules.mesintegration.dto.MesInspectionPushPayload;
import com.xiaobai.workorder.modules.mesintegration.dto.MesReportPushPayload;
import com.xiaobai.workorder.modules.mesintegration.dto.MesStatusPushPayload;
import com.xiaobai.workorder.modules.mesintegration.entity.MesOrderMapping;
import com.xiaobai.workorder.modules.mesintegration.entity.MesSyncLog;
import com.xiaobai.workorder.modules.mesintegration.repository.MesOrderMappingMapper;
import com.xiaobai.workorder.modules.inspection.entity.InspectionRecord;
import com.xiaobai.workorder.modules.operation.entity.Operation;
import com.xiaobai.workorder.modules.operation.repository.OperationMapper;
import com.xiaobai.workorder.modules.report.entity.ReportRecord;
import com.xiaobai.workorder.modules.report.repository.ReportRecordMapper;
import com.xiaobai.workorder.modules.user.entity.User;
import com.xiaobai.workorder.modules.user.repository.UserMapper;
import com.xiaobai.workorder.modules.workorder.entity.WorkOrder;
import com.xiaobai.workorder.modules.workorder.repository.WorkOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Handles all outbound pushes from this system to the upstream MES.
 * Uses ObjectProvider for MesApiClient so the application starts even
 * when integration is disabled (ConditionalOnProperty).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MesOutboundService {

    private final MesSyncLogService syncLogService;
    private final MesOrderMappingMapper mappingMapper;
    private final WorkOrderMapper workOrderMapper;
    private final OperationMapper operationMapper;
    private final ReportRecordMapper reportRecordMapper;
    private final UserMapper userMapper;
    private final ObjectProvider<MesApiClient> mesApiClientProvider;

    @Value("${app.mes.integration.max-retries:5}")
    private int maxRetries;

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ---------------------------------------------------------------
    //  Report push
    // ---------------------------------------------------------------

    public void pushReport(Long reportRecordId) {
        MesApiClient client = mesApiClientProvider.getIfAvailable();
        if (client == null) {
            log.debug("MES integration disabled – skip report push for record {}", reportRecordId);
            return;
        }

        ReportRecord record = reportRecordMapper.selectById(reportRecordId);
        if (record == null) {
            log.warn("ReportRecord {} not found, skip push", reportRecordId);
            return;
        }

        WorkOrder workOrder = workOrderMapper.selectById(record.getWorkOrderId());
        Operation operation = operationMapper.selectById(record.getOperationId());
        User user = userMapper.selectById(record.getUserId());
        MesOrderMapping mapping = mappingMapper
                .findByLocalOrderId(record.getWorkOrderId()).orElse(null);

        MesReportPushPayload payload = new MesReportPushPayload();
        payload.setMesOrderId(mapping != null ? mapping.getMesOrderId() : null);
        payload.setMesOrderNumber(mapping != null ? mapping.getMesOrderNumber() : null);
        payload.setLocalOrderNumber(workOrder != null ? workOrder.getOrderNumber() : null);
        payload.setOperationId(record.getOperationId());
        payload.setOperationNumber(operation != null ? operation.getOperationNumber() : null);
        payload.setOperationName(operation != null ? operation.getOperationName() : null);
        payload.setReportRecordId(reportRecordId);
        payload.setEmployeeNumber(user != null ? user.getEmployeeNumber() : null);
        payload.setRealName(user != null ? user.getRealName() : null);
        payload.setReportedQuantity(record.getReportedQuantity());
        payload.setQualifiedQuantity(record.getQualifiedQuantity());
        payload.setDefectQuantity(record.getDefectQuantity());
        payload.setReportTime(record.getReportTime());
        payload.setNotes(record.getNotes());

        String businessKey = "REPORT-" + reportRecordId;
        MesSyncLog logEntry = syncLogService.createPending(
                MesSyncType.REPORT_PUSH, MesSyncDirection.OUTBOUND, businessKey, payload, maxRetries);

        try {
            String response = client.pushReport(payload);
            syncLogService.markSuccess(logEntry.getId(), response);
            log.info("Report {} pushed to MES successfully", reportRecordId);
        } catch (Exception e) {
            syncLogService.markFailed(logEntry.getId(), e.getMessage());
            log.error("Report {} push to MES failed: {}", reportRecordId, e.getMessage());
        }
    }

    // ---------------------------------------------------------------
    //  Status push
    // ---------------------------------------------------------------

    public void pushStatusChange(Long workOrderId, String previousStatus,
                                  String currentStatus, String changedBy) {
        MesApiClient client = mesApiClientProvider.getIfAvailable();
        if (client == null) {
            log.debug("MES integration disabled – skip status push for order {}", workOrderId);
            return;
        }

        WorkOrder workOrder = workOrderMapper.selectById(workOrderId);
        if (workOrder == null) return;

        MesOrderMapping mapping = mappingMapper.findByLocalOrderId(workOrderId).orElse(null);

        MesStatusPushPayload payload = new MesStatusPushPayload();
        payload.setMesOrderId(mapping != null ? mapping.getMesOrderId() : null);
        payload.setMesOrderNumber(mapping != null ? mapping.getMesOrderNumber() : null);
        payload.setLocalOrderNumber(workOrder.getOrderNumber());
        payload.setPreviousStatus(previousStatus);
        payload.setCurrentStatus(currentStatus);
        payload.setChangedBy(changedBy);
        payload.setChangedAt(LocalDateTime.now().format(DT_FMT));

        String businessKey = workOrder.getOrderNumber() + "-STATUS-" + currentStatus;
        MesSyncLog logEntry = syncLogService.createPending(
                MesSyncType.STATUS_PUSH, MesSyncDirection.OUTBOUND, businessKey, payload, maxRetries);

        try {
            String response = client.pushStatus(payload);
            syncLogService.markSuccess(logEntry.getId(), response);
            log.info("Status change for order {} pushed to MES: {} -> {}",
                    workOrder.getOrderNumber(), previousStatus, currentStatus);
        } catch (Exception e) {
            syncLogService.markFailed(logEntry.getId(), e.getMessage());
            log.error("Status push for order {} failed: {}", workOrder.getOrderNumber(), e.getMessage());
        }
    }

    // ---------------------------------------------------------------
    //  Inspection push
    // ---------------------------------------------------------------

    public void pushInspection(Long inspectionRecordId,
                                InspectionRecord inspectionRecord) {
        MesApiClient client = mesApiClientProvider.getIfAvailable();
        if (client == null) {
            log.debug("MES integration disabled – skip inspection push {}", inspectionRecordId);
            return;
        }

        WorkOrder workOrder = workOrderMapper.selectById(inspectionRecord.getWorkOrderId());
        MesOrderMapping mapping = mappingMapper
                .findByLocalOrderId(inspectionRecord.getWorkOrderId()).orElse(null);
        User inspector = inspectionRecord.getInspectorId() != null
                ? userMapper.selectById(inspectionRecord.getInspectorId()) : null;

        MesInspectionPushPayload payload = new MesInspectionPushPayload();
        payload.setMesOrderId(mapping != null ? mapping.getMesOrderId() : null);
        payload.setMesOrderNumber(mapping != null ? mapping.getMesOrderNumber() : null);
        payload.setLocalOrderNumber(workOrder != null ? workOrder.getOrderNumber() : null);
        payload.setInspectionRecordId(inspectionRecordId);
        payload.setInspectionResult(inspectionRecord.getInspectionResult());
        payload.setInspectedQuantity(inspectionRecord.getInspectedQuantity());
        payload.setQualifiedQuantity(inspectionRecord.getQualifiedQuantity());
        payload.setDefectQuantity(inspectionRecord.getDefectQuantity());
        payload.setDefectReason(inspectionRecord.getDefectReason());
        payload.setInspectorEmployeeNumber(inspector != null ? inspector.getEmployeeNumber() : null);
        payload.setInspectionTime(inspectionRecord.getInspectionTime());

        String businessKey = "INSPECTION-" + inspectionRecordId;
        MesSyncLog logEntry = syncLogService.createPending(
                MesSyncType.INSPECTION_PUSH, MesSyncDirection.OUTBOUND, businessKey, payload, maxRetries);

        try {
            String response = client.pushInspection(payload);
            syncLogService.markSuccess(logEntry.getId(), response);
            log.info("Inspection {} pushed to MES successfully", inspectionRecordId);
        } catch (Exception e) {
            syncLogService.markFailed(logEntry.getId(), e.getMessage());
            log.error("Inspection {} push to MES failed: {}", inspectionRecordId, e.getMessage());
        }
    }
}
