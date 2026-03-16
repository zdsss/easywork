package com.xiaobai.workorder.modules.operation.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaobai.workorder.modules.operation.entity.Operation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OperationMapper extends BaseMapper<Operation> {

    default List<Operation> findByWorkOrderId(Long workOrderId) {
        return selectList(new LambdaQueryWrapper<Operation>()
                .eq(Operation::getWorkOrderId, workOrderId)
                .eq(Operation::getDeleted, 0)
                .orderByAsc(Operation::getSequenceNumber));
    }

    default List<Operation> findByWorkOrderIds(java.util.Collection<Long> workOrderIds) {
        if (workOrderIds == null || workOrderIds.isEmpty()) return java.util.List.of();
        return selectList(new LambdaQueryWrapper<Operation>()
                .in(Operation::getWorkOrderId, workOrderIds)
                .eq(Operation::getDeleted, 0)
                .orderByAsc(Operation::getSequenceNumber));
    }

    @Select("SELECT * FROM operations WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    Operation selectByIdForUpdate(@Param("id") Long id);

    default java.util.Optional<Operation> findByOperationNumber(String operationNumber) {
        return java.util.Optional.ofNullable(
                selectOne(new LambdaQueryWrapper<Operation>()
                        .eq(Operation::getOperationNumber, operationNumber)
                        .eq(Operation::getDeleted, 0)));
    }

    @Select("""
        SELECT op.* FROM operations op
        JOIN operation_assignments oa ON oa.operation_id = op.id AND oa.deleted = 0
        WHERE oa.assignment_type = 'USER' AND oa.user_id = #{userId}
          AND op.work_order_id = #{workOrderId}
          AND op.deleted = 0
          AND op.status NOT IN ('REPORTED', 'INSPECTED', 'TRANSPORTED', 'HANDLED')
        ORDER BY op.sequence_number ASC
        LIMIT 1
        """)
    Operation findEarliestUnfinishedByUserAndWorkOrder(
            @Param("userId") Long userId, @Param("workOrderId") Long workOrderId);

    @Select("""
        SELECT op.* FROM operations op
        JOIN operation_assignments oa ON oa.operation_id = op.id AND oa.deleted = 0
        JOIN team_members tm ON tm.team_id = oa.team_id AND tm.deleted = 0
        WHERE oa.assignment_type = 'TEAM' AND tm.user_id = #{userId}
          AND op.work_order_id = #{workOrderId}
          AND op.deleted = 0
          AND op.status NOT IN ('REPORTED', 'INSPECTED', 'TRANSPORTED', 'HANDLED')
        ORDER BY op.sequence_number ASC
        LIMIT 1
        """)
    Operation findEarliestUnfinishedByTeamUserAndWorkOrder(
            @Param("userId") Long userId, @Param("workOrderId") Long workOrderId);

    /**
     * Returns the earliest NOT_STARTED operation directly assigned to this user for scan-to-start.
     * Prioritizes the front-most (lowest sequenceNumber) unstarted operation.
     */
    @Select("""
        SELECT op.* FROM operations op
        JOIN operation_assignments oa ON oa.operation_id = op.id AND oa.deleted = 0
        WHERE oa.assignment_type = 'USER' AND oa.user_id = #{userId}
          AND op.work_order_id = #{workOrderId}
          AND op.deleted = 0
          AND op.status = 'NOT_STARTED'
        ORDER BY op.sequence_number ASC
        LIMIT 1
        """)
    Operation findEarliestNotStartedByUserAndWorkOrder(
            @Param("userId") Long userId, @Param("workOrderId") Long workOrderId);

    /**
     * Returns the earliest NOT_STARTED operation team-assigned to this user for scan-to-start.
     * Prioritizes the front-most (lowest sequenceNumber) unstarted operation.
     */
    @Select("""
        SELECT op.* FROM operations op
        JOIN operation_assignments oa ON oa.operation_id = op.id AND oa.deleted = 0
        JOIN team_members tm ON tm.team_id = oa.team_id AND tm.deleted = 0
        WHERE oa.assignment_type = 'TEAM' AND tm.user_id = #{userId}
          AND op.work_order_id = #{workOrderId}
          AND op.deleted = 0
          AND op.status = 'NOT_STARTED'
        ORDER BY op.sequence_number ASC
        LIMIT 1
        """)
    Operation findEarliestNotStartedByTeamUserAndWorkOrder(
            @Param("userId") Long userId, @Param("workOrderId") Long workOrderId);
}
