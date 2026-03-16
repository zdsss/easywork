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
                .eq(OperationDependency::getOperationId, operationId)
                .eq(OperationDependency::getDeleted, 0));
    }

    /**
     * Returns dependency records where the given operation is the dependent.
     * I.e., returns all records (operation_id=operationId, predecessor_operation_id=X),
     * which represents "the predecessors that must complete before this operation can start".
     * Use dep.getPredecessorOperationId() to retrieve each predecessor's ID.
     */
    public List<OperationDependency> getPredecessors(Long operationId) {
        return mapper.selectList(new LambdaQueryWrapper<OperationDependency>()
                .eq(OperationDependency::getOperationId, operationId)
                .eq(OperationDependency::getDeleted, 0));
    }
}
