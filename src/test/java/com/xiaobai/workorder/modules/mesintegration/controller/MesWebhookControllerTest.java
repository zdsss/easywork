package com.xiaobai.workorder.modules.mesintegration.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaobai.workorder.config.JwtTokenProvider;
import com.xiaobai.workorder.config.TestSecurityConfig;
import com.xiaobai.workorder.modules.mesintegration.dto.MesImportResponse;
import com.xiaobai.workorder.modules.mesintegration.dto.MesWorkOrderImportRequest;
import com.xiaobai.workorder.modules.mesintegration.service.MesInboundService;
import com.xiaobai.workorder.modules.user.service.UserDetailsServiceImpl;
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
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MesWebhookController.class)
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class MesWebhookControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean MesInboundService inboundService;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean UserDetailsServiceImpl userDetailsServiceImpl;

    @Test
    void importWorkOrder_validRequest_returns200() throws Exception {
        MesImportResponse resp = MesImportResponse.success("MES-001", "MES-WO-001", 1L, "MES-MES-WO-001");
        when(inboundService.importWorkOrder(any(MesWorkOrderImportRequest.class))).thenReturn(resp);

        MesWorkOrderImportRequest req = new MesWorkOrderImportRequest();
        req.setMesOrderId("MES-001");
        req.setMesOrderNumber("MES-WO-001");
        req.setOrderType("PRODUCTION");
        req.setPlannedQuantity(BigDecimal.TEN);

        mockMvc.perform(post("/api/mes/work-orders/import")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.syncStatus").value("SYNCED"));
    }

    @Test
    void importWorkOrder_workerRole_returns403() throws Exception {
        MesWorkOrderImportRequest req = new MesWorkOrderImportRequest();
        req.setMesOrderId("MES-002");
        req.setMesOrderNumber("MES-WO-002");

        mockMvc.perform(post("/api/mes/work-orders/import")
                        .with(user("worker").roles("WORKER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }
}
