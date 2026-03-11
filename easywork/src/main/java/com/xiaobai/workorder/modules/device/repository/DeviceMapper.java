package com.xiaobai.workorder.modules.device.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaobai.workorder.modules.device.entity.Device;
import org.apache.ibatis.annotations.Mapper;

import java.util.Optional;

@Mapper
public interface DeviceMapper extends BaseMapper<Device> {

    default Optional<Device> findByDeviceCode(String deviceCode) {
        return Optional.ofNullable(
                selectOne(new LambdaQueryWrapper<Device>()
                        .eq(Device::getDeviceCode, deviceCode)
                        .eq(Device::getDeleted, 0)));
    }
}
