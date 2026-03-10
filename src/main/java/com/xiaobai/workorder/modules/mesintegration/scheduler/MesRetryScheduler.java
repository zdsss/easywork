package com.xiaobai.workorder.modules.mesintegration.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaobai.workorder.modules.mesintegration.client.MesApiClient;
import com.xiaobai.workorder.modules.mesintegration.constant.MesSyncType;
import com.xiaobai.workorder.modules.mesintegration.dto.MesInspectionPushPayload;
import com.xiaobai.workorder.modules.mesintegration.dto.MesReportPushPayload;
import com.xiaobai.workorder.modules.mesintegration.dto.MesStatusPushPayload;
import com.xiaobai.workorder.modules.mesintegration.entity.MesSyncLog;
import com.xiaobai.workorder.modules.mesintegration.repository.MesSyncLogMapper;
import com.xiaobai.workorder.modules.mesintegration.service.MesSyncLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Retries failed outbound pushes on a fixed schedule.
 * Only active when integration is enabled.
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mes.integration.enabled", havingValue = "true")
public class MesRetryScheduler {

    private final MesSyncLogMapper syncLogMapper;
    private final MesSyncLogService syncLogService;
    private final ObjectProvider<MesApiClient> mesApiClientProvider;
    private final ObjectMapper objectMapper;

    /** Retry every 5 minutes */
    @Scheduled(fixedDelayString = "${app.mes.integration.retry-delay-ms:300000}")
    public void retryFailedPushes() {
        MesApiClient client = mesApiClientProvider.getIfAvailable();
        if (client == null) return;

        List<MesSyncLog> retryable = syncLogMapper.findPendingRetries(3);
        if (retryable.isEmpty()) return;

        log.info("MES retry scheduler: {} records to retry", retryable.size());

        for (MesSyncLog entry : retryable) {
            try {
                String response = dispatchRetry(client, entry);
                syncLogService.markSuccess(entry.getId(), response);
                log.info("Retry success for log {} ({})", entry.getId(), entry.getSyncType());
            } catch (Exception e) {
                syncLogService.markFailed(entry.getId(), e.getMessage());
                log.warn("Retry failed for log {} ({}): {}",
                        entry.getId(), entry.getSyncType(), e.getMessage());
            }
        }
    }

    private String dispatchRetry(MesApiClient client, MesSyncLog entry) throws Exception {
        String payload = entry.getPayload();
        return switch (entry.getSyncType()) {
            case MesSyncType.REPORT_PUSH -> client.pushReport(
                    objectMapper.readValue(payload, MesReportPushPayload.class));
            case MesSyncType.STATUS_PUSH -> client.pushStatus(
                    objectMapper.readValue(payload, MesStatusPushPayload.class));
            case MesSyncType.INSPECTION_PUSH -> client.pushInspection(
                    objectMapper.readValue(payload, MesInspectionPushPayload.class));
            default -> throw new IllegalArgumentException(
                    "No retry handler for syncType: " + entry.getSyncType());
        };
    }
}
