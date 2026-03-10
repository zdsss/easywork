package com.xiaobai.workorder.modules.operation.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaobai.workorder.modules.operation.entity.OperationAssignment;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface OperationAssignmentMapper extends BaseMapper<OperationAssignment> {

    default List<OperationAssignment> findByOperationId(Long operationId) {
        return selectList(new LambdaQueryWrapper<OperationAssignment>()
                .eq(OperationAssignment::getOperationId, operationId)
                .eq(OperationAssignment::getDeleted, 0));
    }
}
