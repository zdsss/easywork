package com.xiaobai.workorder.modules.workorder.statemachine;

import com.xiaobai.workorder.common.enums.WorkOrderStatus;
import com.xiaobai.workorder.common.enums.WorkOrderType;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Centralized work order state machine.
 *
 * <p>Valid transitions by order type:
 * <pre>
 * PRODUCTION:
 *   NOT_STARTED → STARTED
 *   STARTED     → REPORTED
 *   REPORTED    → INSPECT_PASSED | INSPECT_FAILED | SCRAPPED
 *   INSPECT_PASSED → COMPLETED
 *   INSPECT_FAILED → REPORTED  (rework cycle)
 *
 * INSPECTION / TRANSPORT / ANDON:
 *   NOT_STARTED → STARTED
 *   STARTED     → COMPLETED
 * </pre>
 */
@Component
public class WorkOrderStateMachine {

    /** All valid transitions for PRODUCTION order type */
    private static final Map<WorkOrderStatus, Set<WorkOrderStatus>> ALL_TRANSITIONS = Map.of(
        WorkOrderStatus.NOT_STARTED,    Set.of(WorkOrderStatus.STARTED),
        WorkOrderStatus.STARTED,        Set.of(WorkOrderStatus.REPORTED),
        WorkOrderStatus.REPORTED,       Set.of(WorkOrderStatus.INSPECT_PASSED, WorkOrderStatus.INSPECT_FAILED, WorkOrderStatus.SCRAPPED),
        WorkOrderStatus.INSPECT_PASSED, Set.of(WorkOrderStatus.COMPLETED),
        WorkOrderStatus.INSPECT_FAILED, Set.of(WorkOrderStatus.REPORTED),
        WorkOrderStatus.SCRAPPED,       Set.of(),
        WorkOrderStatus.COMPLETED,      Set.of()
    );

    /** Transitions restricted to simple order types (INSPECTION, TRANSPORT, ANDON) */
    private static final Map<WorkOrderStatus, Set<WorkOrderStatus>> SIMPLE_TRANSITIONS = Map.of(
        WorkOrderStatus.NOT_STARTED, Set.of(WorkOrderStatus.STARTED),
        WorkOrderStatus.STARTED,     Set.of(WorkOrderStatus.COMPLETED),
        WorkOrderStatus.COMPLETED,   Set.of()
    );

    /**
     * Returns true if the transition from → to is valid for the given order type.
     */
    public boolean canTransition(WorkOrderStatus from, WorkOrderStatus to, WorkOrderType type) {
        Map<WorkOrderStatus, Set<WorkOrderStatus>> transitions =
            (type == WorkOrderType.PRODUCTION) ? ALL_TRANSITIONS : SIMPLE_TRANSITIONS;
        Set<WorkOrderStatus> allowed = transitions.getOrDefault(from, Set.of());
        return allowed.contains(to);
    }

    /**
     * Returns the set of valid next states for this work order.
     */
    public Set<WorkOrderStatus> allowedTransitions(WorkOrderStatus from, WorkOrderType type) {
        Map<WorkOrderStatus, Set<WorkOrderStatus>> transitions =
            (type == WorkOrderType.PRODUCTION) ? ALL_TRANSITIONS : SIMPLE_TRANSITIONS;
        return Collections.unmodifiableSet(transitions.getOrDefault(from, Set.of()));
    }
}
