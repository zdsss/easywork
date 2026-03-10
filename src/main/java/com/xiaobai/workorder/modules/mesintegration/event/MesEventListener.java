package com.xiaobai.workorder.modules.mesintegration.event;

import com.xiaobai.workorder.modules.mesintegration.service.MesOutboundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for domain events and triggers outbound MES pushes.
 *
 * Key guarantees:
 * - AFTER_COMMIT: pushes only fire when the business transaction has committed,
 *   so MES always receives data that is durably persisted.
 * - @Async: the push runs in a separate thread, so it never blocks or rolls back
 *   the caller's transaction.
 * - If the push fails it is logged via MesSyncLogService and picked up by
 *   MesRetryScheduler on the next retry cycle.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MesEventListener {

    private final MesOutboundService mesOutboundService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReportSaved(ReportRecordSavedEvent event) {
        log.debug("MES event: report record {} saved, pushing to MES", event.getReportRecordId());
        mesOutboundService.pushReport(event.getReportRecordId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWorkOrderStatusChanged(WorkOrderStatusChangedEvent event) {
        log.debug("MES event: work order {} status {} -> {}",
                event.getWorkOrderId(), event.getPreviousStatus(), event.getCurrentStatus());
        mesOutboundService.pushStatusChange(
                event.getWorkOrderId(),
                event.getPreviousStatus(),
                event.getCurrentStatus(),
                event.getChangedBy());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInspectionSaved(InspectionRecordSavedEvent event) {
        log.debug("MES event: inspection record {} saved, pushing to MES",
                event.getInspectionRecordId());
        mesOutboundService.pushInspection(
                event.getInspectionRecordId(),
                event.getInspectionRecord());
    }
}
