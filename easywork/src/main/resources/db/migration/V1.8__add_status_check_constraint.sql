-- V1.8: Add CHECK constraint to enforce valid work_order status values
-- Valid statuses: NOT_STARTED, STARTED, REPORTED, INSPECT_PASSED, INSPECT_FAILED, SCRAPPED, COMPLETED
-- Note: PRODUCTION orders use all statuses; INSPECTION/TRANSPORT/ANDON only use NOT_STARTED, STARTED, COMPLETED
-- Application-layer enforcement (WorkOrderStateMachine) handles per-orderType restrictions;
-- this constraint ensures only valid enum values are stored.

ALTER TABLE work_orders
    ADD CONSTRAINT chk_work_order_status
    CHECK (status IN ('NOT_STARTED', 'STARTED', 'REPORTED', 'INSPECT_PASSED', 'INSPECT_FAILED', 'SCRAPPED', 'COMPLETED'));

-- Operations status values used across all order types:
--   NOT_STARTED, STARTED, REPORTED (production), INSPECTED (inspection), TRANSPORTED (transport), HANDLED (andon), COMPLETED
-- Application-layer code enforces per-orderType restrictions; this constraint ensures only known values are stored.

ALTER TABLE operations
    ADD CONSTRAINT chk_operation_status
    CHECK (status IN ('NOT_STARTED', 'STARTED', 'REPORTED', 'INSPECTED', 'TRANSPORTED', 'HANDLED', 'COMPLETED'));
