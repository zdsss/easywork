package com.xiaobai.workorder.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/api/auth/login";
    private static final String DEVICE_PATH_PREFIX = "/api/device/";

    @Value("${app.security.rate-limit.login-max-requests:10}")
    private int loginMaxRequests;

    @Value("${app.security.rate-limit.login-window-seconds:60}")
    private int loginWindowSeconds;

    @Value("${app.security.rate-limit.device-max-requests:60}")
    private int deviceMaxRequests;

    @Value("${app.security.rate-limit.device-window-seconds:60}")
    private int deviceWindowSeconds;

    private final Cache<String, Bucket> loginBuckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .maximumSize(100_000)
            .build();

    private final Cache<String, Bucket> deviceBuckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .maximumSize(100_000)
            .build();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String uri = request.getRequestURI();

        if (LOGIN_PATH.equals(uri) && "POST".equals(request.getMethod())) {
            String ip = getClientIp(request);
            Bucket bucket = loginBuckets.get(ip, k -> createBucket(loginMaxRequests, loginWindowSeconds));
            if (!bucket.tryConsume(1)) {
                rejectTooManyRequests(response, "Too many login attempts. Please try again later.");
                return;
            }
        } else if (uri.startsWith(DEVICE_PATH_PREFIX)) {
            String ip = getClientIp(request);
            Bucket bucket = deviceBuckets.get(ip, k -> createBucket(deviceMaxRequests, deviceWindowSeconds));
            if (!bucket.tryConsume(1)) {
                rejectTooManyRequests(response, "Too many requests. Please slow down.");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void rejectTooManyRequests(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }

    private Bucket createBucket(int capacity, int windowSeconds) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillIntervally(capacity, Duration.ofSeconds(windowSeconds))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    // Use remoteAddr only. X-Forwarded-For is client-controlled and spoofable.
    // Configure a reverse proxy at the infrastructure layer to set the real client IP
    // via server.forward-headers-strategy=NATIVE in application.yml if needed.
    private String getClientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
