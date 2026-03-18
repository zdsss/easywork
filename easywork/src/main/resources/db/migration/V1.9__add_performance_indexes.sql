-- V1.9: Add performance indexes for critical queries
-- CTO Review P0: Fix N+1 queries and slow list operations

-- Index for work order list filtering by status and type
CREATE INDEX idx_work_orders_status_type ON work_orders(status, order_type);

-- Index for operation lookups by work order
CREATE INDEX idx_operations_work_order_status ON operations(work_order_id, status);

-- Index for report record aggregation
CREATE INDEX idx_report_records_operation_undone ON report_records(operation_id, is_undone);

-- Index for worker's assigned work orders
CREATE INDEX idx_operation_assignments_user ON operation_assignments(user_id);
