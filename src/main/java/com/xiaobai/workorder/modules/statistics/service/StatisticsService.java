package com.xiaobai.workorder.modules.statistics.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiaobai.workorder.modules.statistics.dto.StatisticsDTO;
import com.xiaobai.workorder.modules.workorder.entity.WorkOrder;
import com.xiaobai.workorder.modules.workorder.repository.WorkOrderMapper;
import com.xiaobai.workorder.modules.report.entity.ReportRecord;
import com.xiaobai.workorder.modules.report.repository.ReportRecordMapper;
import com.xiaobai.workorder.modules.user.entity.User;
import com.xiaobai.workorder.modules.user.repository.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final WorkOrderMapper workOrderMapper;
    private final ReportRecordMapper reportRecordMapper;
    private final UserMapper userMapper;

    public StatisticsDTO getDashboardStats() {
        List<WorkOrder> allOrders = workOrderMapper.selectList(
                new LambdaQueryWrapper<WorkOrder>().eq(WorkOrder::getDeleted, 0));

        StatisticsDTO stats = new StatisticsDTO();
        stats.setTotalWorkOrders((long) allOrders.size());
        stats.setNotStartedCount(allOrders.stream()
                .filter(o -> "NOT_STARTED".equals(o.getStatus())).count());
        stats.setStartedCount(allOrders.stream()
                .filter(o -> "STARTED".equals(o.getStatus())).count());
        stats.setReportedCount(allOrders.stream()
                .filter(o -> "REPORTED".equals(o.getStatus())).count());
        stats.setCompletedCount(allOrders.stream()
                .filter(o -> "INSPECT_PASSED".equals(o.getStatus())
                        || "COMPLETED".equals(o.getStatus())).count());

        if (!allOrders.isEmpty()) {
            long done = stats.getReportedCount() + stats.getCompletedCount();
            stats.setOverallCompletionRate(
                    BigDecimal.valueOf(done * 100L)
                            .divide(BigDecimal.valueOf(allOrders.size()), 1, RoundingMode.HALF_UP));
        } else {
            stats.setOverallCompletionRate(BigDecimal.ZERO);
        }

        // Type stats
        Map<String, List<WorkOrder>> byType = allOrders.stream()
                .collect(Collectors.groupingBy(WorkOrder::getOrderType));
        List<StatisticsDTO.WorkOrderTypeStat> typeStats = new ArrayList<>();
        byType.forEach((type, orders) -> {
            StatisticsDTO.WorkOrderTypeStat ts = new StatisticsDTO.WorkOrderTypeStat();
            ts.setOrderType(type);
            ts.setCount((long) orders.size());
            ts.setCompletedCount(orders.stream()
                    .filter(o -> "REPORTED".equals(o.getStatus())
                            || "INSPECT_PASSED".equals(o.getStatus())
                            || "COMPLETED".equals(o.getStatus())).count());
            typeStats.add(ts);
        });
        stats.setTypeStats(typeStats);

        // Worker stats
        List<ReportRecord> allReports = reportRecordMapper.selectList(
                new LambdaQueryWrapper<ReportRecord>()
                        .eq(ReportRecord::getIsUndone, false)
                        .eq(ReportRecord::getDeleted, 0));

        Map<Long, List<ReportRecord>> byUser = allReports.stream()
                .collect(Collectors.groupingBy(ReportRecord::getUserId));
        List<StatisticsDTO.WorkerStat> workerStats = new ArrayList<>();
        byUser.forEach((userId, reports) -> {
            User user = userMapper.selectById(userId);
            if (user != null) {
                StatisticsDTO.WorkerStat ws = new StatisticsDTO.WorkerStat();
                ws.setUserId(userId);
                ws.setRealName(user.getRealName());
                ws.setEmployeeNumber(user.getEmployeeNumber());
                ws.setReportCount((long) reports.size());
                ws.setTotalReported(reports.stream()
                        .map(ReportRecord::getReportedQuantity)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
                workerStats.add(ws);
            }
        });
        stats.setWorkerStats(workerStats);

        return stats;
    }
}
