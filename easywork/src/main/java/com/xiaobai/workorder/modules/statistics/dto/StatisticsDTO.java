package com.xiaobai.workorder.modules.statistics.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class StatisticsDTO {
    private Long totalWorkOrders;
    private Long notStartedCount;
    private Long startedCount;
    private Long reportedCount;
    private Long completedCount;
    private BigDecimal overallCompletionRate;
    private List<WorkOrderTypeStat> typeStats;
    private List<WorkerStat> workerStats;

    @Data
    public static class WorkOrderTypeStat {
        private String orderType;
        private Long count;
        private Long completedCount;
    }

    @Data
    public static class WorkerStat {
        private Long userId;
        private String realName;
        private String employeeNumber;
        private Long reportCount;
        private BigDecimal totalReported;
    }
}
