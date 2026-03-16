package com.xiaobai.workorder.modules.audit.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaobai.workorder.modules.audit.service.AuditLogService;
import com.xiaobai.workorder.modules.operation.entity.Operation;
import com.xiaobai.workorder.modules.operation.repository.OperationMapper;
import com.xiaobai.workorder.modules.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final OperationMapper operationMapper;

    /**
     * Wraps the audited method in a transaction so that the before-state snapshot,
     * the business logic, and the after-state snapshot are all read within the same
     * transaction boundary – eliminating the race condition where another concurrent
     * transaction could modify the row between the before-state read and the actual update.
     */
    @Around("@annotation(auditable)")
    @Transactional
    public Object logOperation(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        Long userId = getCurrentUserId();
        String ipAddress = getClientIp();

        // Capture before-state: extract operationId from args and query current status
        String beforeState = null;
        Long targetId = null;
        try {
            Long operationId = extractOperationId(joinPoint.getArgs());
            if (operationId != null) {
                targetId = operationId;
                Operation before = operationMapper.selectById(operationId);
                if (before != null) {
                    beforeState = objectMapper.writeValueAsString(
                        Map.of("status", before.getStatus(),
                               "completedQuantity", before.getCompletedQuantity()));
                }
            }
        } catch (Exception e) {
            log.debug("Failed to capture before-state", e);
        }

        Object result = joinPoint.proceed();

        // Capture after-state
        try {
            if (targetId == null) {
                targetId = extractTargetId(result);
            }
            String afterState = null;
            if (targetId != null) {
                Operation after = operationMapper.selectById(targetId);
                if (after != null) {
                    afterState = objectMapper.writeValueAsString(
                        Map.of("status", after.getStatus(),
                               "completedQuantity", after.getCompletedQuantity()));
                }
            } else {
                afterState = result != null ? objectMapper.writeValueAsString(result) : null;
            }
            auditLogService.log(userId, auditable.operation(), auditable.targetType(),
                    targetId, beforeState, afterState, ipAddress, null);
        } catch (Exception e) {
            log.error("Failed to log operation", e);
        }

        return result;
    }

    /**
     * Extracts the operationId from method arguments.
     * Handles: Long (direct id), or objects with a getOperationId() method.
     */
    private Long extractOperationId(Object[] args) {
        if (args == null || args.length == 0) return null;
        Object first = args[0];
        if (first instanceof Long) {
            return (Long) first;
        }
        // Try getOperationId() for DTO objects (ReportRequest, UndoReportRequest)
        try {
            return (Long) first.getClass().getMethod("getOperationId").invoke(first);
        } catch (Exception e) {
            return null;
        }
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User) {
            return ((User) auth.getPrincipal()).getId();
        }
        return null;
    }

    private String getClientIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            return request.getRemoteAddr();
        }
        return null;
    }

    private Long extractTargetId(Object result) {
        try {
            return (Long) result.getClass().getMethod("getId").invoke(result);
        } catch (Exception e) {
            return null;
        }
    }
}
