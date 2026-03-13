package com.xiaobai.workorder.modules.audit.service;

import com.xiaobai.workorder.modules.audit.entity.OperationLog;
import com.xiaobai.workorder.modules.audit.repository.OperationLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final OperationLogMapper mapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(Long userId, String operationType, String targetType, Long targetId,
                    String beforeState, String afterState, String ipAddress, String deviceId) {
        OperationLog log = new OperationLog();
        log.setUserId(userId);
        log.setOperationType(operationType);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setBeforeState(beforeState);
        log.setAfterState(afterState);
        log.setIpAddress(ipAddress);
        log.setDeviceId(deviceId);
        mapper.insert(log);
    }
}
