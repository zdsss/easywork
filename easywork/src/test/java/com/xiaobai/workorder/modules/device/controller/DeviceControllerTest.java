package com.xiaobai.workorder.modules.device.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaobai.workorder.common.util.SecurityUtils;
import com.xiaobai.workorder.config.JwtTokenProvider;
import com.xiaobai.workorder.config.TestSecurityConfig;
import com.xiaobai.workorder.modules.auth.dto.LoginRequest;
import com.xiaobai.workorder.modules.auth.dto.LoginResponse;
import com.xiaobai.workorder.modules.auth.service.AuthService;
import com.xiaobai.workorder.modules.call.entity.CallRecord;
import com.xiaobai.workorder.modules.call.service.CallService;
import com.xiaobai.workorder.modules.device.service.DeviceService;
import com.xiaobai.workorder.modules.report.dto.ReportRequest;
import com.xiaobai.workorder.modules.report.dto.UndoReportRequest;
import com.xiaobai.workorder.modules.report.entity.ReportRecord;
import com.xiaobai.workorder.modules.report.service.ReportService;
import com.xiaobai.workorder.modules.user.service.UserDetailsServiceImpl;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DeviceController.class)
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class DeviceControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean AuthService authService;
    @MockBean WorkOrderService workOrderService;
    @MockBean ReportService reportService;
    @MockBean CallService callService;
    @MockBean DeviceService deviceService;
    @MockBean SecurityUtils securityUtils;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean UserDetailsServiceImpl userDetailsServiceImpl;

    @BeforeEach
    void setUp() {
        when(securityUtils.getCurrentUserId()).thenReturn(10L);
    }

    @Test
    void login_validCredentials_returns200() throws Exception {
        LoginResponse resp = new LoginResponse("mock-token", "EMP001", "Worker One", "WORKER", 1L);
        when(authService.login(any(LoginRequest.class))).thenReturn(resp);

        LoginRequest req = new LoginRequest();
        req.setEmployeeNumber("EMP001");
        req.setPassword("password");

        mockMvc.perform(post("/api/device/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("mock-token"));
    }

    @Test
    void getWorkOrders_workerRole_returns200() throws Exception {
        WorkOrderDTO dto = buildWorkOrderDTO(1L);
        when(workOrderService.getAssignedWorkOrders(10L)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/device/work-orders")
                        .with(user("worker").roles("WORKER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1));
    }

    @Test
    void startWork_validOperationId_returns200() throws Exception {
        when(reportService.startWork(anyLong(), anyLong())).thenReturn(null);

        mockMvc.perform(post("/api/device/start")
                        .with(user("worker").roles("WORKER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("operationId", 1L))))
                .andExpect(status().isOk());
    }

    @Test
    void reportWork_validRequest_returns200() throws Exception {
        ReportRecord record = new ReportRecord();
        record.setId(1L);
        when(reportService.reportWork(any(ReportRequest.class), anyLong(), any()))
                .thenReturn(record);

        ReportRequest req = new ReportRequest();
        req.setOperationId(1L);
        req.setReportedQuantity(new BigDecimal("5"));

        mockMvc.perform(post("/api/device/report")
                        .with(user("worker").roles("WORKER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void scanStart_validBarcode_returns200() throws Exception {
        WorkOrderDTO dto = buildWorkOrderDTO(1L);
        dto.setOperations(List.of());
        when(workOrderService.getWorkOrderByBarcode(any(), anyLong())).thenReturn(dto);
        when(workOrderService.getWorkOrderById(1L)).thenReturn(dto);

        mockMvc.perform(post("/api/device/scan/start")
                        .with(user("worker").roles("WORKER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("barcode", "WO-001"))))
                .andExpect(status().isOk());
    }

    @Test
    void scanReport_validBarcode_returns200() throws Exception {
        WorkOrderDTO dto = buildWorkOrderDTO(1L);
        dto.setOperations(List.of());
        when(workOrderService.getWorkOrderByBarcode(any(), anyLong())).thenReturn(dto);
        when(workOrderService.getWorkOrderById(1L)).thenReturn(dto);

        mockMvc.perform(post("/api/device/scan/report")
                        .with(user("worker").roles("WORKER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("barcode", "WO-001"))))
                .andExpect(status().isOk());
    }

    @Test
    void undoReport_validRequest_returns200() throws Exception {
        UndoReportRequest req = new UndoReportRequest();
        req.setOperationId(1L);
        req.setUndoReason("Mistake");

        mockMvc.perform(post("/api/device/report/undo")
                        .with(user("worker").roles("WORKER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void callAndon_validRequest_returns200() throws Exception {
        CallRecord record = new CallRecord();
        record.setId(1L);
        record.setCallType("ANDON");
        when(callService.createCall(any(), anyLong())).thenReturn(record);

        mockMvc.perform(post("/api/device/call/andon")
                        .with(user("worker").roles("WORKER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("workOrderId", 1L))))
                .andExpect(status().isOk());
    }

    @Test
    void callInspection_validRequest_returns200() throws Exception {
        CallRecord record = new CallRecord();
        record.setId(2L);
        record.setCallType("INSPECTION");
        when(callService.createCall(any(), anyLong())).thenReturn(record);

        mockMvc.perform(post("/api/device/call/inspection")
                        .with(user("worker").roles("WORKER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("workOrderId", 1L))))
                .andExpect(status().isOk());
    }

    @Test
    void callTransport_validRequest_returns200() throws Exception {
        CallRecord record = new CallRecord();
        record.setId(3L);
        record.setCallType("TRANSPORT");
        when(callService.createCall(any(), anyLong())).thenReturn(record);

        mockMvc.perform(post("/api/device/call/transport")
                        .with(user("worker").roles("WORKER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("workOrderId", 1L))))
                .andExpect(status().isOk());
    }

    @Test
    void getWorkOrders_noAuthentication_returns403() throws Exception {
        mockMvc.perform(get("/api/device/work-orders"))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private WorkOrderDTO buildWorkOrderDTO(Long id) {
        WorkOrderDTO dto = new WorkOrderDTO();
        dto.setId(id);
        dto.setOrderNumber("WO-00" + id);
        dto.setStatus("NOT_STARTED");
        return dto;
    }
}
