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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Retries failed outbound pushes using exponential backoff.
 *
 * The scheduler fires every {@code app.mes.integration.retry-delay-ms} milliseconds
 * (which doubles as the maximum per-record delay cap). For each RETRYING record it
 * computes the delay for that particular attempt:
 *
 *   delay = min(initialDelayMs * backoffMultiplier^retryCount, retryDelayMs)
 *
 * A record is skipped when insufficient time has passed since its last attempt.
 * After {@code max-retries} attempts the record is permanently marked FAILED and
 * no further retries are attempted.
 *
 * Only active when {@code app.mes.integration.enabled=true}.
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

    /** Maximum delay between retries (ms); also the scheduler poll interval. */
    @Value("${app.mes.integration.retry-delay-ms:300000}")
    private long retryDelayMs;

    /** Maximum number of retry attempts before a record is permanently failed. */
    @Value("${app.mes.integration.max-retries:5}")
    private int maxRetries;

    /** Initial delay before the first retry attempt (ms). */
    @Value("${app.mes.integration.initial-delay-ms:5000}")
    private long initialDelayMs;

    /** Multiplier applied to the delay on each successive attempt. */
    @Value("${app.mes.integration.backoff-multiplier:2.0}")
    private double backoffMultiplier;

    /**
     * Polls for retryable records and dispatches those whose backoff window has elapsed.
     * The fixed-delay is set to {@code retry-delay-ms} so we poll at most once per max-delay cycle.
     */
    @Scheduled(fixedDelayString = "${app.mes.integration.retry-delay-ms:300000}")
    public void retryFailedPushes() {
        MesApiClient client = mesApiClientProvider.getIfAvailable();
        if (client == null) return;

        // Only RETRYING records are eligible. FAILED is a terminal exhaustion state set by markExhausted().
        // Records transition: PENDING → (first fail) → RETRYING → ... → (exhausted) → FAILED
        List<MesSyncLog> retryable = syncLogMapper.findPendingRetries(maxRetries);
        if (retryable.isEmpty()) return;

        log.info("MES retry scheduler: {} candidate records found (maxRetries={})", retryable.size(), maxRetries);

        for (MesSyncLog entry : retryable) {
            int attempt = entry.getRetryCount();   // number of attempts already made

            // Check if this record has exhausted all retries
            if (attempt >= maxRetries) {
                log.warn("MES sync exhausted after {} retries for record {}: {}",
                        maxRetries, entry.getId(), entry.getErrorMessage());
                syncLogService.markExhausted(entry.getId(), entry.getErrorMessage());
                continue;
            }

            // Compute the required wait since the last attempt:
            //   delay(attempt) = min(initialDelayMs * backoffMultiplier^attempt, retryDelayMs)
            long backoffMs = computeBackoff(attempt);
            if (!isDue(entry, backoffMs)) {
                log.debug("MES record {} not due yet (attempt={}, backoffMs={})", entry.getId(), attempt, backoffMs);
                continue;
            }

            try {
                String response = dispatchRetry(client, entry);
                syncLogService.markSuccess(entry.getId(), response);
                log.info("MES retry success for log {} ({}) on attempt {}",
                        entry.getId(), entry.getSyncType(), attempt + 1);
            } catch (Exception e) {
                // Increment attempt count first (markFailed bumps retryCount and sets RETRYING)
                syncLogService.markFailed(entry.getId(), e.getMessage());
                int nextAttempt = attempt + 1;
                if (nextAttempt >= maxRetries) {
                    // All retries exhausted — permanently fail the record
                    log.warn("MES sync exhausted after {} retries for record {}: {}",
                            maxRetries, entry.getId(), e.getMessage());
                    syncLogService.markExhausted(entry.getId(), e.getMessage());
                } else {
                    long nextBackoffMs = computeBackoff(nextAttempt);
                    log.warn("MES retry failed for log {} ({}) on attempt {} – next retry in ~{}s: {}",
                            entry.getId(), entry.getSyncType(), nextAttempt,
                            nextBackoffMs / 1000, e.getMessage());
                }
            }
        }
    }

    /**
     * Computes the exponential backoff delay for the given attempt index.
     * delay = min(initialDelayMs * backoffMultiplier^attempt, retryDelayMs)
     *
     * @param attempt  zero-based attempt index (0 = first retry)
     * @return delay in milliseconds, capped at retryDelayMs
     */
    private long computeBackoff(int attempt) {
        double delay = initialDelayMs * Math.pow(backoffMultiplier, attempt);
        return Math.min((long) delay, retryDelayMs);
    }

    /**
     * Returns true if enough time has elapsed since the record's last update
     * to satisfy the backoff requirement for this attempt.
     */
    private boolean isDue(MesSyncLog entry, long backoffMs) {
        LocalDateTime lastUpdated = entry.getUpdatedAt();
        if (lastUpdated == null) {
            // No update timestamp — treat as immediately due
            return true;
        }
        long elapsedMs = ChronoUnit.MILLIS.between(lastUpdated, LocalDateTime.now());
        return elapsedMs >= backoffMs;
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
