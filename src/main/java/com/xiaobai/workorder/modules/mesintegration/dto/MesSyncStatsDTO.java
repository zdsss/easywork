package com.xiaobai.workorder.modules.mesintegration.dto;

import lombok.Data;
import java.util.Map;

@Data
public class MesSyncStatsDTO {
    private long totalLogs;
    private long pendingCount;
    private long successCount;
    private long failedCount;
    private long retryingCount;
    private Map<String, Long> countBySyncType;
}
