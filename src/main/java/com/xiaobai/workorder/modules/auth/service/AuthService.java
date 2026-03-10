package com.xiaobai.workorder.modules.auth.service;

import com.xiaobai.workorder.common.exception.BusinessException;
import com.xiaobai.workorder.config.JwtTokenProvider;
import com.xiaobai.workorder.modules.auth.dto.LoginRequest;
import com.xiaobai.workorder.modules.auth.dto.LoginResponse;
import com.xiaobai.workorder.modules.device.service.DeviceService;
import com.xiaobai.workorder.modules.user.entity.User;
import com.xiaobai.workorder.modules.user.repository.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserMapper userMapper;
    private final DeviceService deviceService;

    public LoginResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmployeeNumber(), request.getPassword()));
        } catch (AuthenticationException e) {
            throw new BusinessException(401, "Invalid employee number or password");
        }

        User user = userMapper.findByEmployeeNumber(request.getEmployeeNumber())
                .orElseThrow(() -> new BusinessException("User not found"));

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(403, "User account is disabled");
        }

        // Record device login if device code provided
        if (request.getDeviceCode() != null && !request.getDeviceCode().isBlank()) {
            deviceService.recordLogin(request.getDeviceCode(), user.getId());
        }

        String token = jwtTokenProvider.generateToken(
                user.getEmployeeNumber(), user.getId(), user.getRole());

        log.info("User {} logged in successfully", user.getEmployeeNumber());

        return new LoginResponse(token, user.getEmployeeNumber(),
                user.getRealName(), user.getRole(), user.getId());
    }
}
