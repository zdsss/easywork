package com.xiaobai.workorder.modules.operation.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaobai.workorder.modules.operation.entity.ReworkRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ReworkRecordMapper extends BaseMapper<ReworkRecord> {
}
