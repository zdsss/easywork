package com.xiaobai.workorder.modules.inspection.controller;

import com.xiaobai.workorder.common.response.ApiResponse;
import com.xiaobai.workorder.common.util.SecurityUtils;
import com.xiaobai.workorder.modules.inspection.dto.InspectionRequest;
import com.xiaobai.workorder.modules.inspection.entity.InspectionRecord;
import com.xiaobai.workorder.modules.inspection.service.InspectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin - Inspection", description = "Inspection management APIs")
@RestController
@RequestMapping("/api/admin/inspections")
@RequiredArgsConstructor
public class InspectionController {

    private final InspectionService inspectionService;
    private final SecurityUtils securityUtils;

    @Operation(summary = "Submit inspection result for a work order")
    @PostMapping
    public ApiResponse<InspectionRecord> submitInspection(
            @Valid @RequestBody InspectionRequest request) {
        Long inspectorId = securityUtils.getCurrentUserId();
        return ApiResponse.success(inspectionService.submitInspection(request, inspectorId));
    }
}
