package com.xiaobai.workorder.modules.call.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaobai.workorder.common.exception.BusinessException;
import com.xiaobai.workorder.modules.call.dto.CallRecordDTO;
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
import java.util.List;
import java.util.stream.Collectors;

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
    public CallRecordDTO handleCall(Long callId, Long handlerId) {
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
        return toDTO(record);
    }

    @Transactional
    public CallRecordDTO completeCall(Long callId, Long handlerId, String handleResult) {
        CallRecord record = callRecordMapper.selectById(callId);
        if (record == null || record.getDeleted() == 1) {
            throw new BusinessException("Call record not found: " + callId);
        }
        if (!"HANDLING".equals(record.getStatus())) {
            throw new BusinessException("Call must be in HANDLING status to complete");
        }
        record.setStatus("HANDLED");
        record.setHandlerId(handlerId);
        record.setHandleResult(handleResult);
        record.setCompleteTime(LocalDateTime.now());
        callRecordMapper.updateById(record);
        return toDTO(record);
    }

    public List<CallRecordDTO> listCalls(int page, int size, String status) {
        LambdaQueryWrapper<CallRecord> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(CallRecord::getStatus, status);
        }
        wrapper.orderByDesc(CallRecord::getCallTime);

        Page<CallRecord> pageResult = callRecordMapper.selectPage(new Page<>(page, size), wrapper);
        return pageResult.getRecords().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public CallRecordDTO getCallById(Long callId) {
        CallRecord record = callRecordMapper.selectById(callId);
        if (record == null || record.getDeleted() == 1) {
            throw new BusinessException("Call record not found: " + callId);
        }
        return toDTO(record);
    }

    private CallRecordDTO toDTO(CallRecord record) {
        CallRecordDTO dto = new CallRecordDTO();
        dto.setId(record.getId());
        dto.setWorkOrderId(record.getWorkOrderId());
        dto.setOperationId(record.getOperationId());
        dto.setCallType(record.getCallType());
        dto.setCallerId(record.getCallerId());
        dto.setHandlerId(record.getHandlerId());
        dto.setStatus(record.getStatus());
        dto.setCallTime(record.getCallTime());
        dto.setHandleTime(record.getHandleTime());
        dto.setCompleteTime(record.getCompleteTime());
        dto.setDescription(record.getDescription());
        dto.setHandleResult(record.getHandleResult());
        dto.setNotes(record.getNotes());
        return dto;
    }
}
