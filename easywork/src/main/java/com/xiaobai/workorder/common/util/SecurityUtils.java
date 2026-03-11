package com.xiaobai.workorder.common.util;

import com.xiaobai.workorder.config.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class SecurityUtils {

    private final JwtTokenProvider jwtTokenProvider;

    public Long getCurrentUserId() {
        // First try SecurityContextHolder — works in both request threads and async threads
        // (JwtAuthenticationFilter stores the userId in authentication.details)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof Long) {
            return (Long) auth.getDetails();
        }
        // Fall back to extracting userId directly from the request token
        HttpServletRequest request = getCurrentRequest();
        if (request != null) {
            String token = getTokenFromRequest(request);
            if (StringUtils.hasText(token)) {
                return jwtTokenProvider.extractUserId(token);
            }
        }
        return null;
    }

    public String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        return null;
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private HttpServletRequest getCurrentRequest() {
        try {
            return ((jakarta.servlet.http.HttpServletRequest)
                    org.springframework.web.context.request.RequestContextHolder
                            .currentRequestAttributes()
                            .resolveReference("request"));
        } catch (Exception e) {
            return null;
        }
    }
}
