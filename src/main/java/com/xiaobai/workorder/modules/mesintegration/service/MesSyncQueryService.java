package com.xiaobai.workorder.modules.mesintegration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaobai.workorder.modules.mesintegration.constant.MesSyncStatus;
import com.xiaobai.workorder.modules.mesintegration.constant.MesSyncType;
import com.xiaobai.workorder.modules.mesintegration.dto.MesSyncLogDTO;
import com.xiaobai.workorder.modules.mesintegration.dto.MesSyncStatsDTO;
import com.xiaobai.workorder.modules.mesintegration.entity.MesSyncLog;
import com.xiaobai.workorder.modules.mesintegration.repository.MesSyncLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Query-side service: exposes sync log history and stats for the Admin UI.
 */
@Service
@RequiredArgsConstructor
public class MesSyncQueryService {

    private final MesSyncLogMapper syncLogMapper;

    public Page<MesSyncLogDTO> listLogs(int page, int size,
                                          String syncType, String status, String direction) {
        Page<MesSyncLog> rawPage = syncLogMapper.findByFilters(
                new Page<>(page, size), syncType, status, direction);
        Page<MesSyncLogDTO> result = new Page<>(page, size, rawPage.getTotal());
        result.setRecords(rawPage.getRecords().stream().map(this::toDTO).toList());
        return result;
    }

    public MesSyncStatsDTO getStats() {
        MesSyncStatsDTO stats = new MesSyncStatsDTO();

        long total = syncLogMapper.selectCount(
                new LambdaQueryWrapper<MesSyncLog>().eq(MesSyncLog::getDeleted, 0));
        stats.setTotalLogs(total);
        stats.setPendingCount(syncLogMapper.countByStatus(MesSyncStatus.PENDING));
        stats.setSuccessCount(syncLogMapper.countByStatus(MesSyncStatus.SUCCESS));
        stats.setFailedCount(syncLogMapper.countByStatus(MesSyncStatus.FAILED));
        stats.setRetryingCount(syncLogMapper.countByStatus(MesSyncStatus.RETRYING));

        // Count by sync type
        List<String> types = Arrays.asList(
                MesSyncType.WORK_ORDER_IMPORT,
                MesSyncType.REPORT_PUSH,
                MesSyncType.STATUS_PUSH,
                MesSyncType.INSPECTION_PUSH);
        Map<String, Long> countByType = types.stream().collect(Collectors.toMap(
                t -> t,
                t -> syncLogMapper.selectCount(
                        new LambdaQueryWrapper<MesSyncLog>()
                                .eq(MesSyncLog::getSyncType, t)
                                .eq(MesSyncLog::getDeleted, 0))
        ));
        stats.setCountBySyncType(countByType);
        return stats;
    }

    private MesSyncLogDTO toDTO(MesSyncLog log) {
        MesSyncLogDTO dto = new MesSyncLogDTO();
        dto.setId(log.getId());
        dto.setSyncType(log.getSyncType());
        dto.setDirection(log.getDirection());
        dto.setSourceSystem(log.getSourceSystem());
        dto.setTargetSystem(log.getTargetSystem());
        dto.setBusinessKey(log.getBusinessKey());
        dto.setStatus(log.getStatus());
        dto.setRetryCount(log.getRetryCount());
        dto.setMaxRetries(log.getMaxRetries());
        dto.setErrorMessage(log.getErrorMessage());
        dto.setSyncedAt(log.getSyncedAt());
        dto.setCreatedAt(log.getCreatedAt());
        return dto;
    }
}
