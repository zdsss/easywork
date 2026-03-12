package com.xiaobai.workorder.modules.call.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaobai.workorder.common.util.SecurityUtils;
import com.xiaobai.workorder.config.JwtTokenProvider;
import com.xiaobai.workorder.config.TestSecurityConfig;
import com.xiaobai.workorder.modules.call.dto.CallRecordDTO;
import com.xiaobai.workorder.modules.call.dto.HandleCallRequest;
import com.xiaobai.workorder.modules.call.service.CallService;
import com.xiaobai.workorder.modules.user.service.UserDetailsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminCallController.class)
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class AdminCallControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean CallService callService;
    @MockBean SecurityUtils securityUtils;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean UserDetailsServiceImpl userDetailsServiceImpl;

    @BeforeEach
    void setUp() {
        when(securityUtils.getCurrentUserId()).thenReturn(1L);
    }

    @Test
    void listCalls_returns200WithList() throws Exception {
        when(callService.listCalls(anyInt(), anyInt(), nullable(String.class)))
                .thenReturn(List.of(buildCallRecordDTO(1L, "NOT_HANDLED")));

        mockMvc.perform(get("/api/admin/calls")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("NOT_HANDLED"));
    }

    @Test
    void handleCall_validId_returns200() throws Exception {
        CallRecordDTO dto = buildCallRecordDTO(1L, "HANDLING");
        when(callService.handleCall(1L, 1L)).thenReturn(dto);

        mockMvc.perform(put("/api/admin/calls/1/handle")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("HANDLING"));
    }

    @Test
    void completeCall_withResult_returns200() throws Exception {
        CallRecordDTO dto = buildCallRecordDTO(1L, "HANDLED");
        dto.setHandleResult("Fixed");
        when(callService.completeCall(eq(1L), eq(1L), any())).thenReturn(dto);

        HandleCallRequest req = new HandleCallRequest();
        req.setHandleResult("Fixed");

        mockMvc.perform(put("/api/admin/calls/1/complete")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("HANDLED"))
                .andExpect(jsonPath("$.data.handleResult").value("Fixed"));
    }

    @Test
    void handleCall_workerRole_returns403() throws Exception {
        mockMvc.perform(put("/api/admin/calls/1/handle")
                        .with(user("worker").roles("WORKER")))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private CallRecordDTO buildCallRecordDTO(Long id, String status) {
        CallRecordDTO dto = new CallRecordDTO();
        dto.setId(id);
        dto.setStatus(status);
        dto.setCallType("ANDON");
        return dto;
    }
}
