package com.xiaobai.workorder.modules.inspection.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaobai.workorder.modules.inspection.entity.InspectionRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InspectionRecordMapper extends BaseMapper<InspectionRecord> {
}
