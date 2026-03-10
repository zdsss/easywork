package com.xiaobai.workorder.modules.call.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaobai.workorder.modules.call.entity.CallRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CallRecordMapper extends BaseMapper<CallRecord> {
}
