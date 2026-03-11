package com.xiaobai.workorder.modules.workorder.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaobai.workorder.common.util.SecurityUtils;
import com.xiaobai.workorder.config.JwtTokenProvider;
import com.xiaobai.workorder.config.TestSecurityConfig;
import com.xiaobai.workorder.modules.user.service.UserDetailsServiceImpl;
import com.xiaobai.workorder.modules.workorder.dto.AssignWorkOrderRequest;
import com.xiaobai.workorder.modules.workorder.dto.CreateWorkOrderRequest;
import com.xiaobai.workorder.modules.workorder.dto.WorkOrderDTO;
import com.xiaobai.workorder.modules.workorder.service.WorkOrderService;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminWorkOrderController.class)
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class AdminWorkOrderControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean WorkOrderService workOrderService;
    @MockBean SecurityUtils securityUtils;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean UserDetailsServiceImpl userDetailsServiceImpl;

    @BeforeEach
    void setUp() {
        when(securityUtils.getCurrentUserId()).thenReturn(1L);
    }

    @Test
    void createWorkOrder_validRequest_returns200() throws Exception {
        WorkOrderDTO dto = buildWorkOrderDTO(1L, "WO-001");
        when(workOrderService.createWorkOrder(any(), anyLong())).thenReturn(dto);

        CreateWorkOrderRequest req = buildCreateRequest("WO-001");

        mockMvc.perform(post("/api/admin/work-orders")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNumber").value("WO-001"));
    }

    @Test
    void createWorkOrder_missingOrderNumber_returns400() throws Exception {
        CreateWorkOrderRequest req = new CreateWorkOrderRequest();
        req.setOrderType("PRODUCTION");
        req.setPlannedQuantity(BigDecimal.TEN);

        mockMvc.perform(post("/api/admin/work-orders")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listWorkOrders_returns200WithList() throws Exception {
        when(workOrderService.listAllWorkOrders(anyInt(), anyInt(), nullable(String.class)))
                .thenReturn(List.of(buildWorkOrderDTO(1L, "WO-001")));

        mockMvc.perform(get("/api/admin/work-orders")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].orderNumber").value("WO-001"));
    }

    @Test
    void getWorkOrder_existingId_returns200() throws Exception {
        when(workOrderService.getWorkOrderById(1L)).thenReturn(buildWorkOrderDTO(1L, "WO-001"));

        mockMvc.perform(get("/api/admin/work-orders/1")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void assignWorkOrder_validRequest_returns200() throws Exception {
        doNothing().when(workOrderService).assignWorkOrder(any());

        AssignWorkOrderRequest req = new AssignWorkOrderRequest();
        req.setOperationId(1L);
        req.setAssignmentType("USER");
        req.setUserIds(List.of(10L));

        mockMvc.perform(post("/api/admin/work-orders/assign")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void createWorkOrder_workerRole_returns403() throws Exception {
        CreateWorkOrderRequest req = buildCreateRequest("WO-001");

        mockMvc.perform(post("/api/admin/work-orders")
                        .with(user("worker").roles("WORKER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private WorkOrderDTO buildWorkOrderDTO(Long id, String orderNumber) {
        WorkOrderDTO dto = new WorkOrderDTO();
        dto.setId(id);
        dto.setOrderNumber(orderNumber);
        dto.setStatus("NOT_STARTED");
        dto.setOrderType("PRODUCTION");
        return dto;
    }

    private CreateWorkOrderRequest buildCreateRequest(String orderNumber) {
        CreateWorkOrderRequest req = new CreateWorkOrderRequest();
        req.setOrderNumber(orderNumber);
        req.setOrderType("PRODUCTION");
        req.setPlannedQuantity(BigDecimal.TEN);
        return req;
    }
}
