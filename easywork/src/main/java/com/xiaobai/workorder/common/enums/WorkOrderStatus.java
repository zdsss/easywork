package com.xiaobai.workorder.common.enums;

/**
 * Work order lifecycle status values.
 *
 * <pre>
 * PRODUCTION:
 *   NOT_STARTED → STARTED → REPORTED → INSPECT_PASSED → COMPLETED
 *                                     → INSPECT_FAILED → (rework → REPORTED)
 *                                     → SCRAPPED
 * INSPECTION / TRANSPORT / ANDON:
 *   NOT_STARTED → STARTED → COMPLETED
 * </pre>
 */
public enum WorkOrderStatus {
    NOT_STARTED,
    STARTED,
    REPORTED,
    INSPECT_PASSED,
    INSPECT_FAILED,
    SCRAPPED,
    COMPLETED
}
