package com.xiaobai.workorder.modules.report.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaobai.workorder.modules.report.entity.ReportRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Mapper
public interface ReportRecordMapper extends BaseMapper<ReportRecord> {

    default List<ReportRecord> findActiveByOperationId(Long operationId) {
        return selectList(new LambdaQueryWrapper<ReportRecord>()
                .eq(ReportRecord::getOperationId, operationId)
                .eq(ReportRecord::getIsUndone, false)
                .eq(ReportRecord::getDeleted, 0)
                .orderByDesc(ReportRecord::getReportTime));
    }

    default Optional<ReportRecord> findLatestByOperationIdAndUser(Long operationId, Long userId) {
        return Optional.ofNullable(
                selectOne(new LambdaQueryWrapper<ReportRecord>()
                        .eq(ReportRecord::getOperationId, operationId)
                        .eq(ReportRecord::getUserId, userId)
                        .eq(ReportRecord::getIsUndone, false)
                        .eq(ReportRecord::getDeleted, 0)
                        .orderByDesc(ReportRecord::getReportTime)
                        .last("LIMIT 1")));
    }

    @Select("""
        SELECT COALESCE(SUM(reported_quantity), 0) FROM report_records
        WHERE operation_id = #{operationId} AND is_undone = false AND deleted = 0
        """)
    BigDecimal sumReportedQuantityByOperationId(@Param("operationId") Long operationId);
}
