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
}
