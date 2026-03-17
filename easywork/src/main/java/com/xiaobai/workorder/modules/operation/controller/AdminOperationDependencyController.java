package com.xiaobai.workorder.modules.operation.controller;

import com.xiaobai.workorder.common.enums.DependencyType;
import com.xiaobai.workorder.common.response.ApiResponse;
import com.xiaobai.workorder.modules.operation.entity.OperationDependency;
import com.xiaobai.workorder.modules.operation.service.OperationDependencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin - Operation Dependencies", description = "Operation dependency management")
@RestController
@RequestMapping("/api/admin/operation-dependencies")
@RequiredArgsConstructor
public class AdminOperationDependencyController {

    private final OperationDependencyService service;

    @Operation(summary = "Add operation dependency (type: SERIAL or PARALLEL)")
    @PostMapping
    public ApiResponse<Void> addDependency(
            @RequestParam Long operationId,
            @RequestParam Long predecessorId,
            @RequestParam DependencyType type) {
        service.addDependency(operationId, predecessorId, type);
        return ApiResponse.success(null);
    }

    @Operation(summary = "Get operation dependencies")
    @GetMapping("/{operationId}")
    public ApiResponse<List<OperationDependency>> getDependencies(@PathVariable Long operationId) {
        return ApiResponse.success(service.getDependencies(operationId));
    }
}
