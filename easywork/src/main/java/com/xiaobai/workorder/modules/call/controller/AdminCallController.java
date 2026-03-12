package com.xiaobai.workorder.modules.call.controller;

import com.xiaobai.workorder.common.exception.BusinessException;
import com.xiaobai.workorder.common.response.ApiResponse;
import com.xiaobai.workorder.common.util.SecurityUtils;
import com.xiaobai.workorder.modules.call.dto.CallRecordDTO;
import com.xiaobai.workorder.modules.call.dto.HandleCallRequest;
import com.xiaobai.workorder.modules.call.service.CallService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Tag(name = "Admin - Calls", description = "Admin APIs for call/andon management")
@RestController
@RequestMapping("/api/admin/calls")
@RequiredArgsConstructor
public class AdminCallController {

    private final CallService callService;
    private final SecurityUtils securityUtils;

    @Operation(summary = "List all call records")
    @GetMapping
    public ApiResponse<List<CallRecordDTO>> listCalls(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(callService.listCalls(page, size, status));
    }

    @Operation(summary = "Handle a call (set status to HANDLING)")
    @PutMapping("/{id}/handle")
    public ApiResponse<CallRecordDTO> handleCall(@PathVariable Long id) {
        Long handlerId = securityUtils.getCurrentUserId();
        if (handlerId == null) {
            throw new BusinessException("Unauthorized: cannot resolve current user");
        }
        return ApiResponse.success(callService.handleCall(id, handlerId));
    }

    @Operation(summary = "Complete a call (set status to HANDLED)")
    @PutMapping("/{id}/complete")
    public ApiResponse<CallRecordDTO> completeCall(
            @PathVariable Long id,
            @RequestBody(required = false) HandleCallRequest request) {
        Long handlerId = securityUtils.getCurrentUserId();
        if (handlerId == null) {
            throw new BusinessException("Unauthorized: cannot resolve current user");
        }
        String handleResult = request != null ? request.getHandleResult() : null;
        return ApiResponse.success(callService.completeCall(id, handlerId, handleResult));
    }
}
