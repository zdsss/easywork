package com.xiaobai.workorder.modules.mesintegration.dto;

import lombok.Data;

/**
 * Response returned after a work order import attempt.
 */
@Data
public class MesImportResponse {
    private String mesOrderId;
    private String mesOrderNumber;
    private Long localOrderId;
    private String localOrderNumber;
    private String syncStatus;
    private String message;

    public static MesImportResponse success(String mesOrderId, String mesOrderNumber,
                                             Long localOrderId, String localOrderNumber) {
        MesImportResponse r = new MesImportResponse();
        r.mesOrderId = mesOrderId;
        r.mesOrderNumber = mesOrderNumber;
        r.localOrderId = localOrderId;
        r.localOrderNumber = localOrderNumber;
        r.syncStatus = "SYNCED";
        r.message = "Import successful";
        return r;
    }

    public static MesImportResponse failed(String mesOrderId, String reason) {
        MesImportResponse r = new MesImportResponse();
        r.mesOrderId = mesOrderId;
        r.syncStatus = "FAILED";
        r.message = reason;
        return r;
    }
}
