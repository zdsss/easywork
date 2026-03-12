package com.xiaobai.workorder.modules.call.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaobai.workorder.common.exception.BusinessException;
import com.xiaobai.workorder.modules.call.dto.CallRecordDTO;
import com.xiaobai.workorder.modules.call.dto.CallRequest;
import com.xiaobai.workorder.modules.call.entity.CallRecord;
import com.xiaobai.workorder.modules.call.repository.CallRecordMapper;
import com.xiaobai.workorder.modules.workorder.entity.WorkOrder;
import com.xiaobai.workorder.modules.workorder.repository.WorkOrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CallServiceTest {

    @Mock CallRecordMapper callRecordMapper;
    @Mock WorkOrderMapper workOrderMapper;

    @InjectMocks CallService callService;

    @Test
    void createCall_validWorkOrder_returnsRecordWithNotHandledStatus() {
        WorkOrder wo = buildWorkOrder(1L);
        when(workOrderMapper.selectById(1L)).thenReturn(wo);

        CallRequest req = new CallRequest();
        req.setWorkOrderId(1L);
        req.setCallType("ANDON");
        req.setDescription("Machine failure");

        CallRecord result = callService.createCall(req, 10L);

        assertThat(result.getStatus()).isEqualTo("NOT_HANDLED");
        assertThat(result.getCallType()).isEqualTo("ANDON");
        verify(callRecordMapper).insert(any(CallRecord.class));
    }

    @Test
    void createCall_workOrderNotFound_throwsBusinessException() {
        when(workOrderMapper.selectById(99L)).thenReturn(null);

        CallRequest req = new CallRequest();
        req.setWorkOrderId(99L);
        req.setCallType("ANDON");

        assertThatThrownBy(() -> callService.createCall(req, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Work order not found");
    }

    @Test
    void handleCall_notHandledRecord_updatesStatusToHandling() {
        CallRecord record = buildCallRecord(1L, "NOT_HANDLED");
        when(callRecordMapper.selectById(1L)).thenReturn(record);

        CallRecordDTO result = callService.handleCall(1L, 20L);

        assertThat(result.getStatus()).isEqualTo("HANDLING");
        assertThat(result.getHandlerId()).isEqualTo(20L);
        verify(callRecordMapper).updateById(any(CallRecord.class));
    }

    @Test
    void completeCall_updatesStatusToHandledWithResult() {
        CallRecord record = buildCallRecord(1L, "HANDLING");
        when(callRecordMapper.selectById(1L)).thenReturn(record);

        CallRecordDTO result = callService.completeCall(1L, 20L, "Machine repaired");

        assertThat(result.getStatus()).isEqualTo("HANDLED");
        assertThat(result.getHandleResult()).isEqualTo("Machine repaired");
        verify(callRecordMapper).updateById(any(CallRecord.class));
    }

    @Test
    void completeCall_notHandlingStatus_throwsException() {
        CallRecord record = buildCallRecord(1L, "NOT_HANDLED");
        when(callRecordMapper.selectById(1L)).thenReturn(record);

        assertThatThrownBy(() -> callService.completeCall(1L, 20L, "done"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("HANDLING");
    }

    @Test
    void listCalls_withoutFilter_returnsList() {
        Page<CallRecord> page = new Page<>();
        page.setRecords(List.of(buildCallRecord(1L, "NOT_HANDLED")));
        when(callRecordMapper.selectPage(any(), any())).thenReturn(page);

        List<CallRecordDTO> result = callService.listCalls(1, 20, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("NOT_HANDLED");
    }

    @Test
    void listCalls_withStatusFilter_returnsFiltered() {
        Page<CallRecord> page = new Page<>();
        page.setRecords(List.of(buildCallRecord(2L, "HANDLING")));
        when(callRecordMapper.selectPage(any(), any())).thenReturn(page);

        List<CallRecordDTO> result = callService.listCalls(1, 20, "HANDLING");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("HANDLING");
    }

    @Test
    void getCallById_validId_returnsDTO() {
        CallRecord record = buildCallRecord(1L, "NOT_HANDLED");
        when(callRecordMapper.selectById(1L)).thenReturn(record);

        CallRecordDTO result = callService.getCallById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getStatus()).isEqualTo("NOT_HANDLED");
    }

    @Test
    void getCallById_deletedCall_throwsException() {
        CallRecord record = buildCallRecord(1L, "NOT_HANDLED");
        record.setDeleted(1);
        when(callRecordMapper.selectById(1L)).thenReturn(record);

        assertThatThrownBy(() -> callService.getCallById(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Call record not found");
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private WorkOrder buildWorkOrder(Long id) {
        WorkOrder wo = new WorkOrder();
        wo.setId(id);
        wo.setDeleted(0);
        return wo;
    }

    private CallRecord buildCallRecord(Long id, String status) {
        CallRecord record = new CallRecord();
        record.setId(id);
        record.setStatus(status);
        record.setDeleted(0);
        return record;
    }
}
