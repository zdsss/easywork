package com.xiaobai.workorder.modules.mesintegration.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaobai.workorder.modules.mesintegration.entity.MesSyncLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MesSyncLogMapper extends BaseMapper<MesSyncLog> {

    default List<MesSyncLog> findPendingRetries(int maxRetries) {
        return selectList(new LambdaQueryWrapper<MesSyncLog>()
                .in(MesSyncLog::getStatus, "FAILED", "RETRYING")
                .lt(MesSyncLog::getRetryCount, maxRetries)
                .eq(MesSyncLog::getDeleted, 0)
                .orderByAsc(MesSyncLog::getCreatedAt));
    }

    default Page<MesSyncLog> findByFilters(Page<MesSyncLog> page,
                                            String syncType,
                                            String status,
                                            String direction) {
        LambdaQueryWrapper<MesSyncLog> wrapper = new LambdaQueryWrapper<MesSyncLog>()
                .eq(MesSyncLog::getDeleted, 0)
                .orderByDesc(MesSyncLog::getCreatedAt);
        if (syncType != null && !syncType.isBlank()) {
            wrapper.eq(MesSyncLog::getSyncType, syncType);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(MesSyncLog::getStatus, status);
        }
        if (direction != null && !direction.isBlank()) {
            wrapper.eq(MesSyncLog::getDirection, direction);
        }
        return selectPage(page, wrapper);
    }

    @Select("""
        SELECT COUNT(*) FROM mes_sync_logs
        WHERE status = #{status} AND deleted = 0
        """)
    long countByStatus(@Param("status") String status);
}
