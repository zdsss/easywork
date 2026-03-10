package com.xiaobai.workorder.modules.mesintegration.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaobai.workorder.modules.mesintegration.dto.MesInspectionPushPayload;
import com.xiaobai.workorder.modules.mesintegration.dto.MesReportPushPayload;
import com.xiaobai.workorder.modules.mesintegration.dto.MesStatusPushPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP client for pushing data out to the upstream MES system.
 * Only active when app.mes.integration.enabled=true.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mes.integration.enabled", havingValue = "true")
public class MesApiClient {

    private final RestTemplate mesRestTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.mes.integration.base-url}")
    private String baseUrl;

    @Value("${app.mes.integration.api-key:}")
    private String apiKey;

    /**
     * Push a completed report record to MES.
     * @return raw response body string, or null on failure
     */
    public String pushReport(MesReportPushPayload payload) {
        return post("/api/workorder/report", payload);
    }

    /**
     * Push a work order status change to MES.
     */
    public String pushStatus(MesStatusPushPayload payload) {
        return post("/api/workorder/status", payload);
    }

    /**
     * Push an inspection result to MES.
     */
    public String pushInspection(MesInspectionPushPayload payload) {
        return post("/api/workorder/inspection", payload);
    }

    private String post(String path, Object payload) {
        String url = baseUrl + path;
        try {
            HttpHeaders headers = buildHeaders();
            String body = objectMapper.writeValueAsString(payload);
            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = mesRestTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);
            log.debug("MES push {} -> HTTP {}", path, response.getStatusCode().value());
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("MES returned HTTP " + response.getStatusCode().value()
                        + ": " + response.getBody());
            }
            return response.getBody();
        } catch (Exception e) {
            log.error("MES push failed [{}]: {}", url, e.getMessage());
            throw new RuntimeException("MES push failed: " + e.getMessage(), e);
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isBlank()) {
            headers.set("X-Api-Key", apiKey);
        }
        return headers;
    }
}
