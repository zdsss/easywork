package com.xiaobai.workorder.modules.statistics.service;

import com.xiaobai.workorder.modules.report.entity.ReportRecord;
import com.xiaobai.workorder.modules.report.repository.ReportRecordMapper;
import com.xiaobai.workorder.modules.statistics.dto.StatisticsDTO;
import com.xiaobai.workorder.modules.user.entity.User;
import com.xiaobai.workorder.modules.user.repository.UserMapper;
import com.xiaobai.workorder.modules.workorder.entity.WorkOrder;
import com.xiaobai.workorder.modules.workorder.repository.WorkOrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {

    @Mock WorkOrderMapper workOrderMapper;
    @Mock ReportRecordMapper reportRecordMapper;
    @Mock UserMapper userMapper;

    @InjectMocks StatisticsService statisticsService;

    @Test
    void getDashboardStats_noData_returnsAllZeros() {
        when(workOrderMapper.selectList(any())).thenReturn(List.of());
        when(reportRecordMapper.selectList(any())).thenReturn(List.of());

        StatisticsDTO stats = statisticsService.getDashboardStats();

        assertThat(stats.getTotalWorkOrders()).isZero();
        assertThat(stats.getNotStartedCount()).isZero();
        assertThat(stats.getStartedCount()).isZero();
        assertThat(stats.getReportedCount()).isZero();
        assertThat(stats.getCompletedCount()).isZero();
        assertThat(stats.getOverallCompletionRate()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getDashboardStats_correctTotalCount() {
        List<WorkOrder> orders = List.of(
                buildWorkOrder(1L, "NOT_STARTED", "PRODUCTION"),
                buildWorkOrder(2L, "STARTED", "PRODUCTION"),
                buildWorkOrder(3L, "REPORTED", "PRODUCTION"));
        when(workOrderMapper.selectList(any())).thenReturn(orders);
        when(reportRecordMapper.selectList(any())).thenReturn(List.of());

        StatisticsDTO stats = statisticsService.getDashboardStats();

        assertThat(stats.getTotalWorkOrders()).isEqualTo(3L);
    }

    @Test
    void getDashboardStats_correctStatusCounts() {
        List<WorkOrder> orders = List.of(
                buildWorkOrder(1L, "NOT_STARTED", "PRODUCTION"),
                buildWorkOrder(2L, "STARTED", "PRODUCTION"),
                buildWorkOrder(3L, "REPORTED", "PRODUCTION"),
                buildWorkOrder(4L, "INSPECT_PASSED", "PRODUCTION"));
        when(workOrderMapper.selectList(any())).thenReturn(orders);
        when(reportRecordMapper.selectList(any())).thenReturn(List.of());

        StatisticsDTO stats = statisticsService.getDashboardStats();

        assertThat(stats.getNotStartedCount()).isEqualTo(1L);
        assertThat(stats.getStartedCount()).isEqualTo(1L);
        assertThat(stats.getReportedCount()).isEqualTo(1L);
        assertThat(stats.getCompletedCount()).isEqualTo(1L);
    }

    @Test
    void getDashboardStats_correctCompletionRate() {
        List<WorkOrder> orders = List.of(
                buildWorkOrder(1L, "NOT_STARTED", "PRODUCTION"),
                buildWorkOrder(2L, "REPORTED", "PRODUCTION"),
                buildWorkOrder(3L, "INSPECT_PASSED", "PRODUCTION"),
                buildWorkOrder(4L, "STARTED", "PRODUCTION"));
        when(workOrderMapper.selectList(any())).thenReturn(orders);
        when(reportRecordMapper.selectList(any())).thenReturn(List.of());

        StatisticsDTO stats = statisticsService.getDashboardStats();

        // (1 reported + 1 inspected_passed) / 4 * 100 = 50.0
        assertThat(stats.getOverallCompletionRate()).isEqualByComparingTo("50.0");
    }

    @Test
    void getDashboardStats_typeStatsGroupedByOrderType() {
        List<WorkOrder> orders = List.of(
                buildWorkOrder(1L, "NOT_STARTED", "PRODUCTION"),
                buildWorkOrder(2L, "STARTED", "PRODUCTION"),
                buildWorkOrder(3L, "NOT_STARTED", "INSPECTION"));
        when(workOrderMapper.selectList(any())).thenReturn(orders);
        when(reportRecordMapper.selectList(any())).thenReturn(List.of());

        StatisticsDTO stats = statisticsService.getDashboardStats();

        assertThat(stats.getTypeStats()).hasSize(2);
        StatisticsDTO.WorkOrderTypeStat productionStat = stats.getTypeStats().stream()
                .filter(ts -> "PRODUCTION".equals(ts.getOrderType())).findFirst().orElseThrow();
        assertThat(productionStat.getCount()).isEqualTo(2L);
    }

    @Test
    void getDashboardStats_workerStatsComputedCorrectly() {
        List<WorkOrder> orders = List.of(buildWorkOrder(1L, "REPORTED", "PRODUCTION"));
        when(workOrderMapper.selectList(any())).thenReturn(orders);

        ReportRecord r1 = buildReportRecord(1L, 10L, new BigDecimal("5"));
        ReportRecord r2 = buildReportRecord(2L, 10L, new BigDecimal("3"));
        when(reportRecordMapper.selectList(any())).thenReturn(List.of(r1, r2));

        User user = new User();
        user.setId(10L);
        user.setRealName("Worker One");
        user.setEmployeeNumber("EMP001");
        when(userMapper.selectById(10L)).thenReturn(user);

        StatisticsDTO stats = statisticsService.getDashboardStats();

        assertThat(stats.getWorkerStats()).hasSize(1);
        StatisticsDTO.WorkerStat ws = stats.getWorkerStats().get(0);
        assertThat(ws.getReportCount()).isEqualTo(2L);
        assertThat(ws.getTotalReported()).isEqualByComparingTo("8");
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private WorkOrder buildWorkOrder(Long id, String status, String type) {
        WorkOrder wo = new WorkOrder();
        wo.setId(id);
        wo.setStatus(status);
        wo.setOrderType(type);
        wo.setDeleted(0);
        return wo;
    }

    private ReportRecord buildReportRecord(Long id, Long userId, BigDecimal qty) {
        ReportRecord r = new ReportRecord();
        r.setId(id);
        r.setUserId(userId);
        r.setReportedQuantity(qty);
        r.setIsUndone(false);
        r.setDeleted(0);
        return r;
    }
}
