package com.xiaobai.workorder.modules.statistics.service;

import com.xiaobai.workorder.modules.inspection.repository.InspectionRecordMapper;
import com.xiaobai.workorder.modules.statistics.dto.StatisticsDTO;
import com.xiaobai.workorder.modules.workorder.repository.WorkOrderMapper;
import com.xiaobai.workorder.modules.report.repository.ReportRecordMapper;
import com.xiaobai.workorder.modules.user.entity.User;
import com.xiaobai.workorder.modules.user.repository.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final WorkOrderMapper workOrderMapper;
    private final ReportRecordMapper reportRecordMapper;
    private final UserMapper userMapper;
    private final InspectionRecordMapper inspectionRecordMapper;

    public StatisticsDTO getDashboardStats() {
        // SQL aggregation: one query for counts by status (no full table load)
        List<Map<String, Object>> statusCounts = workOrderMapper.countByStatus();

        Map<String, Long> byStatus = new HashMap<>();
        long total = 0;
        for (Map<String, Object> row : statusCounts) {
            String status = (String) row.get("status");
            long cnt = ((Number) row.get("cnt")).longValue();
            byStatus.put(status, cnt);
            total += cnt;
        }

        StatisticsDTO stats = new StatisticsDTO();
        stats.setTotalWorkOrders(total);
        stats.setNotStartedCount(byStatus.getOrDefault("NOT_STARTED", 0L));
        stats.setStartedCount(byStatus.getOrDefault("STARTED", 0L));
        stats.setReportedCount(byStatus.getOrDefault("REPORTED", 0L));
        stats.setCompletedCount(byStatus.getOrDefault("INSPECT_PASSED", 0L)
                + byStatus.getOrDefault("COMPLETED", 0L));

        if (total > 0) {
            long done = stats.getReportedCount() + stats.getCompletedCount();
            stats.setOverallCompletionRate(
                    BigDecimal.valueOf(done * 100L)
                            .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP));
        } else {
            stats.setOverallCompletionRate(BigDecimal.ZERO);
        }

        // Type stats via SQL aggregation
        List<Map<String, Object>> typeRows = workOrderMapper.countByTypeAndStatus();
        Map<String, StatisticsDTO.WorkOrderTypeStat> typeMap = new HashMap<>();
        for (Map<String, Object> row : typeRows) {
            String type = (String) row.get("order_type");
            String status = (String) row.get("status");
            long cnt = ((Number) row.get("cnt")).longValue();

            StatisticsDTO.WorkOrderTypeStat ts = typeMap.computeIfAbsent(type, k -> {
                StatisticsDTO.WorkOrderTypeStat s = new StatisticsDTO.WorkOrderTypeStat();
                s.setOrderType(k);
                s.setCount(0L);
                s.setCompletedCount(0L);
                return s;
            });
            ts.setCount(ts.getCount() + cnt);
            // completedCount matches the dashboard definition: INSPECT_PASSED + COMPLETED only.
            // REPORTED means work is submitted but pending QC — counted separately via reportedCount.
            if ("INSPECT_PASSED".equals(status) || "COMPLETED".equals(status)) {
                ts.setCompletedCount(ts.getCompletedCount() + cnt);
            }
        }
        stats.setTypeStats(new ArrayList<>(typeMap.values()));

        // Worker stats via SQL aggregation
        List<Map<String, Object>> workerRows = reportRecordMapper.sumByUser();
        List<StatisticsDTO.WorkerStat> workerStats = new ArrayList<>();
        for (Map<String, Object> row : workerRows) {
            // MyBatis with map-underscore-to-camel-case converts user_id → userId
            Object userIdObj = row.getOrDefault("userId", row.get("user_id"));
            if (userIdObj == null) continue;
            Long userId = ((Number) userIdObj).longValue();
            User user = userMapper.selectById(userId);
            if (user != null) {
                StatisticsDTO.WorkerStat ws = new StatisticsDTO.WorkerStat();
                ws.setUserId(userId);
                ws.setRealName(user.getRealName());
                ws.setEmployeeNumber(user.getEmployeeNumber());
                Object reportCountObj = row.getOrDefault("reportCount", row.get("report_count"));
                ws.setReportCount(reportCountObj != null ? ((Number) reportCountObj).longValue() : 0L);
                Object totalObj = row.getOrDefault("totalReported", row.get("total_reported"));
                ws.setTotalReported(totalObj instanceof BigDecimal bd ? bd
                        : totalObj != null ? new BigDecimal(totalObj.toString()) : BigDecimal.ZERO);
                workerStats.add(ws);
            }
        }
        stats.setWorkerStats(workerStats);

        return stats;
    }

    public List<Map<String, Object>> getInspectionTrend(int days) {
        LocalDate startDate = LocalDate.now().minusDays(days - 1);
        List<Map<String, Object>> rows = inspectionRecordMapper.countByDateGrouped(startDate);

        // Build a map keyed by date string, ensuring all days are present
        Map<String, Map<String, Object>> byDate = new LinkedHashMap<>();
        for (int i = days - 1; i >= 0; i--) {
            String dateStr = LocalDate.now().minusDays(i).toString();
            Map<String, Object> entry = new HashMap<>();
            entry.put("date", dateStr);
            entry.put("passed", 0L);
            entry.put("failed", 0L);
            byDate.put(dateStr, entry);
        }

        for (Map<String, Object> row : rows) {
            Object dateObj = row.get("date");
            if (dateObj == null) continue;
            String dateStr = dateObj.toString();
            Map<String, Object> entry = byDate.get(dateStr);
            if (entry == null) continue;
            String result = (String) row.get("result");
            long cnt = ((Number) row.get("cnt")).longValue();
            if ("PASSED".equals(result)) {
                entry.put("passed", cnt);
            } else if ("FAILED".equals(result)) {
                entry.put("failed", cnt);
            }
        }

        return new ArrayList<>(byDate.values());
    }
}
