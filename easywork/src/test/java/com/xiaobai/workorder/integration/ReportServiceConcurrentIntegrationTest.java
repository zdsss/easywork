package com.xiaobai.workorder.integration;

import com.xiaobai.workorder.modules.report.dto.ReportRequest;
import com.xiaobai.workorder.modules.report.repository.ReportRecordMapper;
import com.xiaobai.workorder.modules.report.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ReportService concurrent behavior.
 *
 * Validates that the pessimistic lock (SELECT FOR UPDATE) prevents
 * over-reporting when multiple workers submit simultaneously.
 *
 * Requires: docker compose up -d postgres redis (already running).
 *
 * Covers the P0 risk from CTO review:
 * "check-then-act race condition in ReportService.reportWork()"
 */
class ReportServiceConcurrentIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ReportService reportService;

    @Autowired
    ReportRecordMapper reportRecordMapper;

    private Long operationId;
    private Long userId;
    private static final BigDecimal PLANNED_QUANTITY = new BigDecimal("10.00");

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM report_records");
        jdbc.execute("DELETE FROM operation_assignments");
        jdbc.execute("DELETE FROM inspection_records");
        jdbc.execute("DELETE FROM call_records");
        jdbc.execute("DELETE FROM mes_order_mappings");
        jdbc.execute("DELETE FROM operations");
        jdbc.execute("DELETE FROM work_orders");
        jdbc.execute("DELETE FROM team_members");
        jdbc.execute("DELETE FROM teams");
        jdbc.execute("DELETE FROM users WHERE employee_number != 'ADMIN001'");

        userId = jdbc.queryForObject(
                "INSERT INTO users (employee_number, username, password, real_name, role, status) " +
                "VALUES ('W001', 'worker1', 'pw', 'Worker One', 'WORKER', 'ACTIVE') RETURNING id",
                Long.class);

        Long workOrderId = jdbc.queryForObject(
                "INSERT INTO work_orders (order_number, order_type, planned_quantity, status) " +
                "VALUES ('WO-CONCURRENT', 'PRODUCTION', 10, 'STARTED') RETURNING id",
                Long.class);

        operationId = jdbc.queryForObject(
                "INSERT INTO operations (work_order_id, operation_number, operation_name, " +
                "operation_type, sequence_number, planned_quantity, status) " +
                "VALUES (?, 'WO-CONCURRENT-OP001', 'Machining', 'PRODUCTION', 1, 10, 'STARTED') RETURNING id",
                Long.class, workOrderId);
    }

    @Test
    void concurrentReporting_doesNotExceedPlannedQuantity() throws InterruptedException {
        int threadCount = 5;
        // Each thread tries to report 3 units; total attempt = 15, planned = 10
        BigDecimal reportPerThread = new BigDecimal("3.00");

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await(); // all threads start simultaneously
                    ReportRequest req = new ReportRequest();
                    req.setOperationId(operationId);
                    req.setReportedQuantity(reportPerThread);
                    reportService.reportWork(req, userId, null);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                }
                return null;
            }));
        }

        startLatch.countDown(); // release all threads at once
        for (Future<?> f : futures) {
            try { f.get(); } catch (Exception ignored) {}
        }
        executor.shutdown();

        // Total reported must never exceed planned quantity
        BigDecimal totalReported = reportRecordMapper.sumReportedQuantityByOperationId(operationId);
        assertThat(totalReported.compareTo(PLANNED_QUANTITY)).isLessThanOrEqualTo(0);

        // Some threads must have succeeded (at least 3 units = 1 thread)
        assertThat(successCount.get()).isGreaterThanOrEqualTo(1);

        // Total success * 3 must equal totalReported (no partial writes)
        assertThat(totalReported).isEqualByComparingTo(
                reportPerThread.multiply(BigDecimal.valueOf(successCount.get())));
    }

    @Test
    void reportWork_sumMatchesInsertedRecords() {
        // Sequential: 3 + 4 + 3 = 10 (exactly planned), then 1 more should fail
        reportSingle(new BigDecimal("3.00"));
        reportSingle(new BigDecimal("4.00"));
        reportSingle(new BigDecimal("3.00"));

        BigDecimal total = reportRecordMapper.sumReportedQuantityByOperationId(operationId);
        assertThat(total).isEqualByComparingTo(new BigDecimal("10.00"));

        // Fourth attempt should be rejected (remaining = 0)
        org.junit.jupiter.api.Assertions.assertThrows(
                com.xiaobai.workorder.common.exception.BusinessException.class,
                () -> reportSingle(new BigDecimal("1.00")));
    }

    private void reportSingle(BigDecimal qty) {
        ReportRequest req = new ReportRequest();
        req.setOperationId(operationId);
        req.setReportedQuantity(qty);
        reportService.reportWork(req, userId, null);
    }
}
