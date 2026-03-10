package com.xiaobai.workorder.modules.device.service;

import com.xiaobai.workorder.common.exception.BusinessException;
import com.xiaobai.workorder.modules.device.entity.Device;
import com.xiaobai.workorder.modules.device.repository.DeviceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceMapper deviceMapper;

    @Transactional
    public void recordLogin(String deviceCode, Long userId) {
        Device device = deviceMapper.findByDeviceCode(deviceCode)
                .orElseThrow(() -> new BusinessException("Device not found: " + deviceCode));

        if (!"ACTIVE".equals(device.getStatus())) {
            throw new BusinessException("Device is not active: " + deviceCode);
        }

        device.setLastLoginAt(LocalDateTime.now());
        device.setLastLoginUserId(userId);
        deviceMapper.updateById(device);
        log.info("Device {} login recorded for user {}", deviceCode, userId);
    }

    public Device findByCode(String deviceCode) {
        return deviceMapper.findByDeviceCode(deviceCode)
                .orElseThrow(() -> new BusinessException("Device not found: " + deviceCode));
    }
}
