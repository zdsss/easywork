package com.xiaobai.workorder.integration;

import com.xiaobai.workorder.modules.operation.entity.OperationAssignment;
import com.xiaobai.workorder.modules.operation.repository.OperationAssignmentMapper;
import com.xiaobai.workorder.modules.operation.repository.OperationMapper;
import com.xiaobai.workorder.modules.workorder.entity.WorkOrder;
import com.xiaobai.workorder.modules.workorder.repository.WorkOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests that verify the custom SQL in WorkOrderMapper
 * against the running PostgreSQL database (from docker-compose).
 *
 * Requires: docker compose up -d postgres redis (already running)
 *
 * Covers the P0 risk from CTO review:
 * "findByDirectUserId and findByTeamMemberId — JOIN SQL never verified against a real DB."
 */
class WorkOrderMapperIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    WorkOrderMapper workOrderMapper;

    @Autowired
    OperationMapper operationMapper;

    @Autowired
    OperationAssignmentMapper assignmentMapper;

    // IDs inserted by @BeforeEach
    private Long userId1;
    private Long teamId;
    private Long workOrderDirectId;
    private Long workOrderTeamId;
    private Long workOrderBothId;

    @BeforeEach
    void setUp() {
        // Clean up in reverse dependency order
        jdbc.execute("DELETE FROM operation_assignments");
        jdbc.execute("DELETE FROM report_records");
        jdbc.execute("DELETE FROM inspection_records");
        jdbc.execute("DELETE FROM call_records");
        jdbc.execute("DELETE FROM mes_order_mappings");
        jdbc.execute("DELETE FROM mes_sync_logs");
        jdbc.execute("DELETE FROM operations");
        jdbc.execute("DELETE FROM work_orders");
        jdbc.execute("DELETE FROM team_members");
        jdbc.execute("DELETE FROM teams");
        jdbc.execute("DELETE FROM users WHERE employee_number != 'ADMIN001'");

        // Insert test user
        userId1 = jdbc.queryForObject(
                "INSERT INTO users (employee_number, username, password, real_name, role, status) " +
                "VALUES ('W001', 'worker1', 'pw', 'Worker One', 'WORKER', 'ACTIVE') RETURNING id",
                Long.class);

        Long userId2 = jdbc.queryForObject(
                "INSERT INTO users (employee_number, username, password, real_name, role, status) " +
                "VALUES ('W002', 'worker2', 'pw', 'Worker Two', 'WORKER', 'ACTIVE') RETURNING id",
                Long.class);

        // Insert team and add userId1 as member
        teamId = jdbc.queryForObject(
                "INSERT INTO teams (team_code, team_name, status) VALUES ('T001', 'Team Alpha', 'ACTIVE') RETURNING id",
                Long.class);
        jdbc.update("INSERT INTO team_members (team_id, user_id) VALUES (?, ?)", teamId, userId1);

        // Work order 1: directly assigned to userId1
        workOrderDirectId = createWorkOrder("WO-DIRECT");
        Long opDirectId = createOperation(workOrderDirectId, "WO-DIRECT-OP001");
        jdbc.update("INSERT INTO operation_assignments (operation_id, assignment_type, user_id) VALUES (?, 'USER', ?)",
                opDirectId, userId1);

        // Work order 2: assigned to team (userId1 is a member)
        workOrderTeamId = createWorkOrder("WO-TEAM");
        Long opTeamId = createOperation(workOrderTeamId, "WO-TEAM-OP001");
        jdbc.update("INSERT INTO operation_assignments (operation_id, assignment_type, team_id) VALUES (?, 'TEAM', ?)",
                opTeamId, teamId);

        // Work order 3: appears in both direct AND team results (dedup test)
        workOrderBothId = createWorkOrder("WO-BOTH");
        Long opBothId = createOperation(workOrderBothId, "WO-BOTH-OP001");
        jdbc.update("INSERT INTO operation_assignments (operation_id, assignment_type, user_id) VALUES (?, 'USER', ?)",
                opBothId, userId1);
        jdbc.update("INSERT INTO operation_assignments (operation_id, assignment_type, team_id) VALUES (?, 'TEAM', ?)",
                opBothId, teamId);

        // Work order 4: assigned only to userId2 (should NOT appear for userId1)
        Long workOrderOtherId = createWorkOrder("WO-OTHER");
        Long opOtherId = createOperation(workOrderOtherId, "WO-OTHER-OP001");
        jdbc.update("INSERT INTO operation_assignments (operation_id, assignment_type, user_id) VALUES (?, 'USER', ?)",
                opOtherId, userId2);
    }

    @Test
    void findByDirectUserId_returnsOnlyDirectlyAssignedWorkOrders() {
        List<WorkOrder> result = workOrderMapper.findByDirectUserId(userId1);

        List<Long> ids = result.stream().map(WorkOrder::getId).toList();
        assertThat(ids).containsExactlyInAnyOrder(workOrderDirectId, workOrderBothId);
        assertThat(ids).doesNotContain(workOrderTeamId);
    }

    @Test
    void findByTeamMemberId_returnsTeamAssignedWorkOrders() {
        List<WorkOrder> result = workOrderMapper.findByTeamMemberId(userId1);

        List<Long> ids = result.stream().map(WorkOrder::getId).toList();
        assertThat(ids).containsExactlyInAnyOrder(workOrderTeamId, workOrderBothId);
        assertThat(ids).doesNotContain(workOrderDirectId);
    }

    @Test
    void findByDirectUserId_excludesCompletedWorkOrders() {
        jdbc.update("UPDATE work_orders SET status = 'COMPLETED' WHERE id = ?", workOrderDirectId);

        List<WorkOrder> result = workOrderMapper.findByDirectUserId(userId1);

        List<Long> ids = result.stream().map(WorkOrder::getId).toList();
        assertThat(ids).doesNotContain(workOrderDirectId);
    }

    @Test
    void findByDirectUserId_returnsEmptyForUnassignedUser() {
        Long strangerUserId = jdbc.queryForObject(
                "INSERT INTO users (employee_number, username, password, real_name, role, status) " +
                "VALUES ('W999', 'stranger', 'pw', 'Stranger', 'WORKER', 'ACTIVE') RETURNING id",
                Long.class);

        List<WorkOrder> result = workOrderMapper.findByDirectUserId(strangerUserId);

        assertThat(result).isEmpty();
    }

    @Test
    void countByStatus_returnsCorrectAggregateCounts() {
        // We have 4 work orders: WO-DIRECT, WO-TEAM, WO-BOTH, WO-OTHER — all NOT_STARTED
        List<java.util.Map<String, Object>> counts = workOrderMapper.countByStatus();

        long notStartedCount = counts.stream()
                .filter(r -> "NOT_STARTED".equals(r.get("status")))
                .mapToLong(r -> ((Number) r.get("cnt")).longValue())
                .sum();

        assertThat(notStartedCount).isEqualTo(4);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private Long createWorkOrder(String orderNumber) {
        return jdbc.queryForObject(
                "INSERT INTO work_orders (order_number, order_type, planned_quantity, status) " +
                "VALUES (?, 'PRODUCTION', 10, 'NOT_STARTED') RETURNING id",
                Long.class, orderNumber);
    }

    private Long createOperation(Long workOrderId, String operationNumber) {
        return jdbc.queryForObject(
                "INSERT INTO operations (work_order_id, operation_number, operation_name, " +
                "operation_type, sequence_number, planned_quantity, status) " +
                "VALUES (?, ?, 'Op', 'PRODUCTION', 1, 10, 'NOT_STARTED') RETURNING id",
                Long.class, workOrderId, operationNumber);
    }
}
