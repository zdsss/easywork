package com.xiaobai.workorder.modules.inspection.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaobai.workorder.common.util.SecurityUtils;
import com.xiaobai.workorder.config.JwtTokenProvider;
import com.xiaobai.workorder.config.TestSecurityConfig;
import com.xiaobai.workorder.modules.inspection.dto.InspectionRequest;
import com.xiaobai.workorder.modules.inspection.entity.InspectionRecord;
import com.xiaobai.workorder.modules.inspection.service.InspectionService;
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

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InspectionController.class)
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class InspectionControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean InspectionService inspectionService;
    @MockBean SecurityUtils securityUtils;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean UserDetailsServiceImpl userDetailsServiceImpl;

    @BeforeEach
    void setUp() {
        when(securityUtils.getCurrentUserId()).thenReturn(99L);
    }

    @Test
    void submitInspection_validRequest_returns200() throws Exception {
        InspectionRecord record = new InspectionRecord();
        record.setId(1L);
        record.setWorkOrderId(1L);
        record.setInspectionResult("PASSED");
        when(inspectionService.submitInspection(any(InspectionRequest.class), anyLong()))
                .thenReturn(record);

        InspectionRequest req = new InspectionRequest();
        req.setWorkOrderId(1L);
        req.setInspectionResult("PASSED");
        req.setInspectedQuantity(BigDecimal.TEN);
        req.setQualifiedQuantity(BigDecimal.TEN);
        req.setDefectQuantity(BigDecimal.ZERO);

        mockMvc.perform(post("/api/admin/inspections")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inspectionResult").value("PASSED"));
    }

    @Test
    void submitInspection_missingWorkOrderId_returns400() throws Exception {
        InspectionRequest req = new InspectionRequest();
        req.setInspectionResult("PASSED");

        mockMvc.perform(post("/api/admin/inspections")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitInspection_workerRole_returns403() throws Exception {
        InspectionRequest req = new InspectionRequest();
        req.setWorkOrderId(1L);
        req.setInspectionResult("PASSED");

        mockMvc.perform(post("/api/admin/inspections")
                        .with(user("worker").roles("WORKER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }
}
