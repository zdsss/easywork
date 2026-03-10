package com.xiaobai.workorder.modules.mesintegration.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Published after a work order's status is changed.
 * Consumed by MesEventListener to push the status change to MES.
 */
@Getter
public class WorkOrderStatusChangedEvent extends ApplicationEvent {

    private final Long workOrderId;
    private final String previousStatus;
    private final String currentStatus;
    private final String changedBy;

    public WorkOrderStatusChangedEvent(Object source, Long workOrderId,
                                        String previousStatus, String currentStatus,
                                        String changedBy) {
        super(source);
        this.workOrderId = workOrderId;
        this.previousStatus = previousStatus;
        this.currentStatus = currentStatus;
        this.changedBy = changedBy;
    }
}
