package com.xiaobai.workorder.modules.mesintegration.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Published after a report record is successfully persisted.
 * Consumed by MesEventListener to push the record to MES asynchronously,
 * after the business transaction commits.
 */
@Getter
public class ReportRecordSavedEvent extends ApplicationEvent {

    private final Long reportRecordId;
    private final Long workOrderId;
    private final String workOrderStatus;

    public ReportRecordSavedEvent(Object source, Long reportRecordId,
                                   Long workOrderId, String workOrderStatus) {
        super(source);
        this.reportRecordId = reportRecordId;
        this.workOrderId = workOrderId;
        this.workOrderStatus = workOrderStatus;
    }
}
