package com.xiaobai.workorder.modules.report.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UndoReportRequest {
    @NotNull(message = "Operation ID is required")
    private Long operationId;

    private String undoReason;
}
