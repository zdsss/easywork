package com.xiaobai.workorder.modules.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaobai.workorder.common.exception.BusinessException;
import com.xiaobai.workorder.config.JwtTokenProvider;
import com.xiaobai.workorder.config.TestSecurityConfig;
import com.xiaobai.workorder.modules.auth.dto.LoginRequest;
import com.xiaobai.workorder.modules.auth.dto.LoginResponse;
import com.xiaobai.workorder.modules.auth.service.AuthService;
import com.xiaobai.workorder.modules.user.service.UserDetailsServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean AuthService authService;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean UserDetailsServiceImpl userDetailsServiceImpl;

    @Test
    void login_validCredentials_returns200WithToken() throws Exception {
        LoginResponse loginResponse = new LoginResponse("mock-token", "EMP001", "Test User", "WORKER", 1L);
        when(authService.login(any(LoginRequest.class))).thenReturn(loginResponse);

        LoginRequest req = new LoginRequest();
        req.setEmployeeNumber("EMP001");
        req.setPassword("password");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").value("mock-token"));
    }

    @Test
    void login_emptyEmployeeNumber_returns400() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmployeeNumber("");
        req.setPassword("password");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_emptyPassword_returns400() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmployeeNumber("EMP001");
        req.setPassword("");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_invalidCredentials_returns400() throws Exception {
        when(authService.login(any())).thenThrow(new BusinessException(401, "Invalid credentials"));

        LoginRequest req = new LoginRequest();
        req.setEmployeeNumber("EMP001");
        req.setPassword("wrong");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
