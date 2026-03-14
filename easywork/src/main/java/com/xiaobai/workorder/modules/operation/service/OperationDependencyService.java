package com.xiaobai.workorder.modules.operation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiaobai.workorder.modules.operation.entity.OperationDependency;
import com.xiaobai.workorder.modules.operation.repository.OperationDependencyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OperationDependencyService {

    private final OperationDependencyMapper mapper;

    @Transactional
    public void addDependency(Long operationId, Long predecessorId, String type, String condition) {
        OperationDependency dep = new OperationDependency();
        dep.setOperationId(operationId);
        dep.setPredecessorOperationId(predecessorId);
        dep.setDependencyType(type);
        dep.setConditionExpression(condition);
        mapper.insert(dep);
    }

    public List<OperationDependency> getDependencies(Long operationId) {
        return mapper.selectList(new LambdaQueryWrapper<OperationDependency>()
                .eq(OperationDependency::getOperationId, operationId));
    }

    /**
     * Returns dependency records where the given operation is listed as a predecessor.
     * I.e., returns all records (operation_id=X, predecessor_operation_id=operationId),
     * which represents "operations that depend on (are successors of) the given operation".
     */
    public List<OperationDependency> getPredecessors(Long operationId) {
        return mapper.selectList(new LambdaQueryWrapper<OperationDependency>()
                .eq(OperationDependency::getPredecessorOperationId, operationId));
    }
}
