package com.xiaobai.workorder.common.enums;

/**
 * Operation lifecycle status values.
 *
 * <pre>
 * PRODUCTION operations:
 *   NOT_STARTED → STARTED → REPORTED → COMPLETED
 * INSPECTION operations:
 *   NOT_STARTED → STARTED → INSPECTED → COMPLETED
 * TRANSPORT operations:
 *   NOT_STARTED → STARTED → TRANSPORTED → COMPLETED
 * ANDON operations:
 *   NOT_STARTED → STARTED → HANDLED → COMPLETED
 * </pre>
 */
public enum OperationStatus {
    NOT_STARTED,
    STARTED,
    REPORTED,
    INSPECTED,
    TRANSPORTED,
    HANDLED,
    COMPLETED
}
