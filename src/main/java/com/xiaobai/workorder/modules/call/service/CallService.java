package com.xiaobai.workorder.modules.call.service;

import com.xiaobai.workorder.common.exception.BusinessException;
import com.xiaobai.workorder.modules.call.dto.CallRequest;
import com.xiaobai.workorder.modules.call.entity.CallRecord;
import com.xiaobai.workorder.modules.call.repository.CallRecordMapper;
import com.xiaobai.workorder.modules.workorder.entity.WorkOrder;
import com.xiaobai.workorder.modules.workorder.repository.WorkOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CallService {

    private final CallRecordMapper callRecordMapper;
    private final WorkOrderMapper workOrderMapper;

    @Transactional
    public CallRecord createCall(CallRequest request, Long callerId) {
        WorkOrder workOrder = workOrderMapper.selectById(request.getWorkOrderId());
        if (workOrder == null || workOrder.getDeleted() == 1) {
            throw new BusinessException("Work order not found: " + request.getWorkOrderId());
        }

        CallRecord record = new CallRecord();
        record.setWorkOrderId(request.getWorkOrderId());
        record.setOperationId(request.getOperationId());
        record.setCallType(request.getCallType());
        record.setCallerId(callerId);
        record.setStatus("NOT_HANDLED");
        record.setCallTime(LocalDateTime.now());
        record.setDescription(request.getDescription());
        callRecordMapper.insert(record);

        log.info("Call {} created for work order {} by user {}",
                request.getCallType(), request.getWorkOrderId(), callerId);
        return record;
    }

    @Transactional
    public CallRecord handleCall(Long callId, Long handlerId) {
        CallRecord record = callRecordMapper.selectById(callId);
        if (record == null || record.getDeleted() == 1) {
            throw new BusinessException("Call record not found: " + callId);
        }
        if (!"NOT_HANDLED".equals(record.getStatus())) {
            throw new BusinessException("Call is already being handled or completed");
        }
        record.setStatus("HANDLING");
        record.setHandlerId(handlerId);
        record.setHandleTime(LocalDateTime.now());
        callRecordMapper.updateById(record);
        return record;
    }

    @Transactional
    public CallRecord completeCall(Long callId, Long handlerId, String handleResult) {
        CallRecord record = callRecordMapper.selectById(callId);
        if (record == null || record.getDeleted() == 1) {
            throw new BusinessException("Call record not found: " + callId);
        }
        record.setStatus("HANDLED");
        record.setHandlerId(handlerId);
        record.setHandleResult(handleResult);
        record.setCompleteTime(LocalDateTime.now());
        callRecordMapper.updateById(record);
        return record;
    }
}
