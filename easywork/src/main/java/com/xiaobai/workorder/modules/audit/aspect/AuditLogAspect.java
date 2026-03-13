package com.xiaobai.workorder.modules.audit.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaobai.workorder.modules.audit.service.AuditLogService;
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

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @Around("@annotation(auditable)")
    public Object logOperation(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        Long userId = getCurrentUserId();
        String ipAddress = getClientIp();

        Object result = joinPoint.proceed();

        try {
            String afterState = objectMapper.writeValueAsString(result);
            auditLogService.log(userId, auditable.operation(), auditable.targetType(),
                    extractTargetId(result), null, afterState, ipAddress, null);
        } catch (Exception e) {
            log.error("Failed to log operation", e);
        }

        return result;
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
