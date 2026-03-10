package com.xiaobai.workorder.modules.mesintegration.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaobai.workorder.modules.mesintegration.constant.MesSyncDirection;
import com.xiaobai.workorder.modules.mesintegration.constant.MesSyncStatus;
import com.xiaobai.workorder.modules.mesintegration.entity.MesSyncLog;
import com.xiaobai.workorder.modules.mesintegration.repository.MesSyncLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Manages persistence of sync audit logs.
 * Uses REQUIRES_NEW so that a log entry is always saved
 * even if the outer business transaction rolls back.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MesSyncLogService {

    private final MesSyncLogMapper syncLogMapper;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MesSyncLog createPending(String syncType, String direction,
                                     String businessKey, Object payload) {
        MesSyncLog logEntry = new MesSyncLog();
        logEntry.setSyncType(syncType);
        logEntry.setDirection(direction);
        logEntry.setSourceSystem(MesSyncDirection.OUTBOUND.equals(direction)
                ? "WORKORDER" : "MES");
        logEntry.setTargetSystem(MesSyncDirection.OUTBOUND.equals(direction)
                ? "MES" : "WORKORDER");
        logEntry.setBusinessKey(businessKey);
        logEntry.setPayload(toJson(payload));
        logEntry.setStatus(MesSyncStatus.PENDING);
        logEntry.setRetryCount(0);
        logEntry.setMaxRetries(3);
        syncLogMapper.insert(logEntry);
        return logEntry;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSuccess(Long logId, String responseBody) {
        MesSyncLog logEntry = syncLogMapper.selectById(logId);
        if (logEntry == null) return;
        logEntry.setStatus(MesSyncStatus.SUCCESS);
        logEntry.setResponseBody(responseBody);
        logEntry.setSyncedAt(LocalDateTime.now());
        syncLogMapper.updateById(logEntry);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long logId, String errorMessage) {
        MesSyncLog logEntry = syncLogMapper.selectById(logId);
        if (logEntry == null) return;
        logEntry.setRetryCount(logEntry.getRetryCount() + 1);
        boolean exhausted = logEntry.getRetryCount() >= logEntry.getMaxRetries();
        logEntry.setStatus(exhausted ? MesSyncStatus.FAILED : MesSyncStatus.RETRYING);
        logEntry.setErrorMessage(errorMessage);
        syncLogMapper.updateById(logEntry);
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize payload: {}", e.getMessage());
            return obj.toString();
        }
    }
}
