package com.xiaobai.workorder.modules.auth.service;

import com.xiaobai.workorder.common.exception.BusinessException;
import com.xiaobai.workorder.config.JwtTokenProvider;
import com.xiaobai.workorder.modules.auth.dto.LoginRequest;
import com.xiaobai.workorder.modules.auth.dto.LoginResponse;
import com.xiaobai.workorder.modules.device.service.DeviceService;
import com.xiaobai.workorder.modules.user.entity.User;
import com.xiaobai.workorder.modules.user.repository.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock AuthenticationManager authenticationManager;
    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock UserMapper userMapper;
    @Mock DeviceService deviceService;

    @InjectMocks AuthService authService;

    @Test
    void login_validCredentials_returnsLoginResponse() {
        // authenticate() returns Authentication (not void) - default mock returns null which is fine
        when(authenticationManager.authenticate(any())).thenReturn(null);
        User user = buildUser("EMP001", "WORKER", "ACTIVE");
        when(userMapper.findByEmployeeNumber("EMP001")).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateToken("EMP001", 1L, "WORKER")).thenReturn("mock-token");

        LoginRequest req = new LoginRequest();
        req.setEmployeeNumber("EMP001");
        req.setPassword("password");

        LoginResponse response = authService.login(req);

        assertThat(response.getToken()).isEqualTo("mock-token");
        assertThat(response.getRole()).isEqualTo("WORKER");
        assertThat(response.getEmployeeNumber()).isEqualTo("EMP001");
    }

    @Test
    void login_invalidCredentials_throwsBusinessException() {
        doThrow(new BadCredentialsException("Bad creds"))
                .when(authenticationManager).authenticate(any());

        LoginRequest req = new LoginRequest();
        req.setEmployeeNumber("EMP001");
        req.setPassword("wrong");

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(401));
    }

    @Test
    void login_disabledUser_throwsBusinessException() {
        when(authenticationManager.authenticate(any())).thenReturn(null);
        User user = buildUser("EMP001", "WORKER", "DISABLED");
        when(userMapper.findByEmployeeNumber("EMP001")).thenReturn(Optional.of(user));

        LoginRequest req = new LoginRequest();
        req.setEmployeeNumber("EMP001");
        req.setPassword("password");

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(403));
    }

    @Test
    void login_withDeviceCode_recordsDeviceLogin() {
        when(authenticationManager.authenticate(any())).thenReturn(null);
        User user = buildUser("EMP001", "WORKER", "ACTIVE");
        when(userMapper.findByEmployeeNumber("EMP001")).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateToken(any(), any(), any())).thenReturn("mock-token");

        LoginRequest req = new LoginRequest();
        req.setEmployeeNumber("EMP001");
        req.setPassword("password");
        req.setDeviceCode("DEVICE-001");

        authService.login(req);

        verify(deviceService).recordLogin("DEVICE-001", 1L);
    }

    @Test
    void login_withoutDeviceCode_doesNotRecordDeviceLogin() {
        when(authenticationManager.authenticate(any())).thenReturn(null);
        User user = buildUser("EMP001", "WORKER", "ACTIVE");
        when(userMapper.findByEmployeeNumber("EMP001")).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateToken(any(), any(), any())).thenReturn("mock-token");

        LoginRequest req = new LoginRequest();
        req.setEmployeeNumber("EMP001");
        req.setPassword("password");

        authService.login(req);

        verify(deviceService, never()).recordLogin(any(), any());
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private User buildUser(String empNumber, String role, String status) {
        User user = new User();
        user.setId(1L);
        user.setEmployeeNumber(empNumber);
        user.setUsername(empNumber);
        user.setRealName("Test User");
        user.setRole(role);
        user.setStatus(status);
        user.setDeleted(0);
        return user;
    }
}
