package com.xiaobai.workorder.modules.mesintegration.controller;

import com.xiaobai.workorder.common.response.ApiResponse;
import com.xiaobai.workorder.modules.mesintegration.dto.MesImportResponse;
import com.xiaobai.workorder.modules.mesintegration.dto.MesWorkOrderImportRequest;
import com.xiaobai.workorder.modules.mesintegration.service.MesInboundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Webhook endpoint called by the upstream MES system to push work orders
 * into this system.
 *
 * Authentication: Bearer JWT (same as all other endpoints).
 * The MES system should obtain a token by calling POST /api/auth/login
 * with a dedicated service-account user (role = ADMIN).
 */
@Tag(name = "MES Integration - Inbound", description = "Webhook endpoints for MES to push data in")
@RestController
@RequestMapping("/api/mes")
@RequiredArgsConstructor
public class MesWebhookController {

    private final MesInboundService inboundService;

    @Operation(
            summary = "Import work order from MES",
            description = "MES pushes a work order (with operations and assignments). " +
                          "Idempotent: same mesOrderId is ignored on repeated calls.")
    @PostMapping("/work-orders/import")
    public ApiResponse<MesImportResponse> importWorkOrder(
            @Valid @RequestBody MesWorkOrderImportRequest request) {
        return ApiResponse.success(inboundService.importWorkOrder(request));
    }
}
