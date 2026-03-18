package com.xiaobai.workorder.modules.workorder.statemachine;

import com.xiaobai.workorder.common.enums.WorkOrderStatus;
import com.xiaobai.workorder.common.enums.WorkOrderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkOrderStateMachineTest {

    private WorkOrderStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new WorkOrderStateMachine();
    }

    @Test
    void production_notStartedToStarted_isValid() {
        assertThat(stateMachine.canTransition(
                WorkOrderStatus.NOT_STARTED, WorkOrderStatus.STARTED, WorkOrderType.PRODUCTION))
                .isTrue();
    }

    @Test
    void production_reportedToInspectPassed_isValid() {
        assertThat(stateMachine.canTransition(
                WorkOrderStatus.REPORTED, WorkOrderStatus.INSPECT_PASSED, WorkOrderType.PRODUCTION))
                .isTrue();
    }

    @Test
    void production_startedToCompleted_isInvalid() {
        // PRODUCTION must go through REPORTED first — STARTED → COMPLETED is not a valid direct step
        assertThat(stateMachine.canTransition(
                WorkOrderStatus.STARTED, WorkOrderStatus.COMPLETED, WorkOrderType.PRODUCTION))
                .isFalse();
    }

    @Test
    void andon_startedToCompleted_isValid() {
        assertThat(stateMachine.canTransition(
                WorkOrderStatus.STARTED, WorkOrderStatus.COMPLETED, WorkOrderType.ANDON))
                .isTrue();
    }

    @Test
    void andon_startedToReported_isInvalid() {
        // Simple order types do not have a REPORTED step
        assertThat(stateMachine.canTransition(
                WorkOrderStatus.STARTED, WorkOrderStatus.REPORTED, WorkOrderType.ANDON))
                .isFalse();
    }

    @Test
    void production_inspectFailedToReported_isValid() {
        assertThat(stateMachine.canTransition(
                WorkOrderStatus.INSPECT_FAILED, WorkOrderStatus.REPORTED, WorkOrderType.PRODUCTION))
                .isTrue();
    }

    @Test
    void production_inspectPassedToCompleted_isValid() {
        assertThat(stateMachine.canTransition(
                WorkOrderStatus.INSPECT_PASSED, WorkOrderStatus.COMPLETED, WorkOrderType.PRODUCTION))
                .isTrue();
    }

    @Test
    void allowedTransitions_productionStarted_returnsReportedOnly() {
        assertThat(stateMachine.allowedTransitions(WorkOrderStatus.STARTED, WorkOrderType.PRODUCTION))
                .containsExactly(WorkOrderStatus.REPORTED);
    }

    @Test
    void allowedTransitions_andonStarted_returnsCompletedOnly() {
        assertThat(stateMachine.allowedTransitions(WorkOrderStatus.STARTED, WorkOrderType.ANDON))
                .containsExactly(WorkOrderStatus.COMPLETED);
    }

    @Test
    void allowedTransitions_completed_isEmpty() {
        assertThat(stateMachine.allowedTransitions(WorkOrderStatus.COMPLETED, WorkOrderType.PRODUCTION))
                .isEmpty();
    }
}
