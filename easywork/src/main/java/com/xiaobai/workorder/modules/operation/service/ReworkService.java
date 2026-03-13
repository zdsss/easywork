package com.xiaobai.workorder.modules.operation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiaobai.workorder.modules.operation.entity.ReworkRecord;
import com.xiaobai.workorder.modules.operation.repository.ReworkRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReworkService {

    private final ReworkRecordMapper mapper;

    @Transactional
    public ReworkRecord createRework(Long workOrderId, Long originalOpId, Long reworkOpId,
                                     BigDecimal quantity, String reason) {
        ReworkRecord record = new ReworkRecord();
        record.setWorkOrderId(workOrderId);
        record.setOriginalOperationId(originalOpId);
        record.setReworkOperationId(reworkOpId);
        record.setReworkQuantity(quantity);
        record.setReworkReason(reason);
        record.setReworkTimes(1);
        mapper.insert(record);
        return record;
    }

    public List<ReworkRecord> getByWorkOrder(Long workOrderId) {
        return mapper.selectList(new LambdaQueryWrapper<ReworkRecord>()
                .eq(ReworkRecord::getWorkOrderId, workOrderId));
    }
}
