package com.xiaobai.workorder.modules.inspection.service;

import com.xiaobai.workorder.common.enums.WorkOrderStatus;
import com.xiaobai.workorder.common.exception.BusinessException;
import com.xiaobai.workorder.modules.inspection.dto.InspectionRequest;
import com.xiaobai.workorder.modules.inspection.entity.InspectionRecord;
import com.xiaobai.workorder.modules.inspection.repository.InspectionRecordMapper;
import com.xiaobai.workorder.modules.mesintegration.event.InspectionRecordSavedEvent;
import com.xiaobai.workorder.modules.mesintegration.event.WorkOrderStatusChangedEvent;
import com.xiaobai.workorder.modules.workorder.entity.WorkOrder;
import com.xiaobai.workorder.modules.workorder.repository.WorkOrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InspectionServiceTest {

    @Mock InspectionRecordMapper inspectionRecordMapper;
    @Mock WorkOrderMapper workOrderMapper;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks InspectionService inspectionService;

    @Test
    void submitInspection_passed_updatesWorkOrderToInspectPassed() {
        WorkOrder wo = buildWorkOrder(1L, WorkOrderStatus.REPORTED);
        when(workOrderMapper.selectById(1L)).thenReturn(wo);

        InspectionRequest req = buildRequest(1L, "PASSED");

        InspectionRecord result = inspectionService.submitInspection(req, 99L);

        assertThat(result).isNotNull();
        // Work order is updated in-place
        assertThat(wo.getStatus()).isEqualTo(WorkOrderStatus.INSPECT_PASSED);
        verify(workOrderMapper).updateById((WorkOrder) any(WorkOrder.class));
    }

    @Test
    void submitInspection_failed_updatesWorkOrderToInspectFailed() {
        WorkOrder wo = buildWorkOrder(1L, WorkOrderStatus.REPORTED);
        when(workOrderMapper.selectById(1L)).thenReturn(wo);

        InspectionRequest req = buildRequest(1L, "FAILED");

        inspectionService.submitInspection(req, 99L);

        assertThat(wo.getStatus()).isEqualTo(WorkOrderStatus.INSPECT_FAILED);
    }

    @Test
    void submitInspection_notReportedStatus_throwsBusinessException() {
        WorkOrder wo = buildWorkOrder(1L, WorkOrderStatus.STARTED);
        when(workOrderMapper.selectById(1L)).thenReturn(wo);

        InspectionRequest req = buildRequest(1L, "PASSED");

        assertThatThrownBy(() -> inspectionService.submitInspection(req, 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("REPORTED status");
    }

    @Test
    void submitInspection_workOrderNotFound_throwsBusinessException() {
        when(workOrderMapper.selectById(99L)).thenReturn(null);

        InspectionRequest req = buildRequest(99L, "PASSED");

        assertThatThrownBy(() -> inspectionService.submitInspection(req, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Work order not found");
    }

    @Test
    void submitInspection_publishesInspectionRecordSavedEvent() {
        WorkOrder wo = buildWorkOrder(1L, WorkOrderStatus.REPORTED);
        when(workOrderMapper.selectById(1L)).thenReturn(wo);

        inspectionService.submitInspection(buildRequest(1L, "PASSED"), 99L);

        verify(eventPublisher).publishEvent(any(InspectionRecordSavedEvent.class));
    }

    @Test
    void submitInspection_publishesWorkOrderStatusChangedEvent() {
        WorkOrder wo = buildWorkOrder(1L, WorkOrderStatus.REPORTED);
        when(workOrderMapper.selectById(1L)).thenReturn(wo);

        inspectionService.submitInspection(buildRequest(1L, "PASSED"), 99L);

        verify(eventPublisher).publishEvent(any(WorkOrderStatusChangedEvent.class));
    }

    @Test
    void testSubmitInspection_Rework_SetsInspectFailed() {
        WorkOrder wo = buildWorkOrder(1L, WorkOrderStatus.REPORTED);
        when(workOrderMapper.selectById(1L)).thenReturn(wo);

        InspectionRequest req = buildRequest(1L, "REWORK");

        inspectionService.submitInspection(req, 99L);

        assertThat(wo.getStatus()).isEqualTo(WorkOrderStatus.INSPECT_FAILED);
    }

    @Test
    void testSubmitInspection_Scrap_SetsScrapped() {
        WorkOrder wo = buildWorkOrder(1L, WorkOrderStatus.REPORTED);
        when(workOrderMapper.selectById(1L)).thenReturn(wo);

        InspectionRequest req = buildRequest(1L, "SCRAP_MATERIAL");

        inspectionService.submitInspection(req, 99L);

        assertThat(wo.getStatus()).isEqualTo(WorkOrderStatus.SCRAPPED);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private WorkOrder buildWorkOrder(Long id, WorkOrderStatus status) {
        WorkOrder wo = new WorkOrder();
        wo.setId(id);
        wo.setStatus(status);
        wo.setDeleted(0);
        return wo;
    }

    private InspectionRequest buildRequest(Long workOrderId, String result) {
        InspectionRequest req = new InspectionRequest();
        req.setWorkOrderId(workOrderId);
        req.setInspectionResult(result);
        req.setInspectedQuantity(BigDecimal.TEN);
        req.setQualifiedQuantity(BigDecimal.TEN);
        req.setDefectQuantity(BigDecimal.ZERO);
        return req;
    }
}
