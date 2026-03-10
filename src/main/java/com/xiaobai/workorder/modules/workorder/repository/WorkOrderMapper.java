package com.xiaobai.workorder.modules.workorder.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaobai.workorder.modules.workorder.entity.WorkOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

@Mapper
public interface WorkOrderMapper extends BaseMapper<WorkOrder> {

    default Optional<WorkOrder> findByOrderNumber(String orderNumber) {
        return Optional.ofNullable(
                selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WorkOrder>()
                        .eq(WorkOrder::getOrderNumber, orderNumber)
                        .eq(WorkOrder::getDeleted, 0)));
    }

    @Select("""
        SELECT DISTINCT wo.* FROM work_orders wo
        JOIN operations op ON op.work_order_id = wo.id AND op.deleted = 0
        JOIN operation_assignments oa ON oa.operation_id = op.id AND oa.deleted = 0
        WHERE oa.assignment_type = 'USER' AND oa.user_id = #{userId}
          AND wo.deleted = 0 AND wo.status NOT IN ('COMPLETED', 'INSPECT_FAILED')
        ORDER BY wo.priority DESC, wo.planned_start_time ASC
        """)
    List<WorkOrder> findByDirectUserId(@Param("userId") Long userId);

    @Select("""
        SELECT DISTINCT wo.* FROM work_orders wo
        JOIN operations op ON op.work_order_id = wo.id AND op.deleted = 0
        JOIN operation_assignments oa ON oa.operation_id = op.id AND oa.deleted = 0
        JOIN team_members tm ON tm.team_id = oa.team_id AND tm.deleted = 0
        WHERE oa.assignment_type = 'TEAM' AND tm.user_id = #{userId}
          AND wo.deleted = 0 AND wo.status NOT IN ('COMPLETED', 'INSPECT_FAILED')
        ORDER BY wo.priority DESC, wo.planned_start_time ASC
        """)
    List<WorkOrder> findByTeamMemberId(@Param("userId") Long userId);
}
