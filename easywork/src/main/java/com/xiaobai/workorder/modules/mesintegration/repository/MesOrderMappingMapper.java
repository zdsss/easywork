package com.xiaobai.workorder.modules.mesintegration.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaobai.workorder.modules.mesintegration.entity.MesOrderMapping;
import org.apache.ibatis.annotations.Mapper;

import java.util.Optional;

@Mapper
public interface MesOrderMappingMapper extends BaseMapper<MesOrderMapping> {

    default Optional<MesOrderMapping> findByLocalOrderId(Long localOrderId) {
        return Optional.ofNullable(
                selectOne(new LambdaQueryWrapper<MesOrderMapping>()
                        .eq(MesOrderMapping::getLocalOrderId, localOrderId)
                        .eq(MesOrderMapping::getDeleted, 0)));
    }

    default Optional<MesOrderMapping> findByMesOrderId(String mesOrderId) {
        return Optional.ofNullable(
                selectOne(new LambdaQueryWrapper<MesOrderMapping>()
                        .eq(MesOrderMapping::getMesOrderId, mesOrderId)
                        .eq(MesOrderMapping::getDeleted, 0)));
    }

    default Optional<MesOrderMapping> findByMesOrderNumber(String mesOrderNumber) {
        return Optional.ofNullable(
                selectOne(new LambdaQueryWrapper<MesOrderMapping>()
                        .eq(MesOrderMapping::getMesOrderNumber, mesOrderNumber)
                        .eq(MesOrderMapping::getDeleted, 0)));
    }
}
