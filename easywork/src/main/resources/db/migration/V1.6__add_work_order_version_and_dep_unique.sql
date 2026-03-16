-- P2-A: Add optimistic lock version column to work_orders
ALTER TABLE work_orders ADD COLUMN IF NOT EXISTS version INTEGER DEFAULT 0 NOT NULL;

-- P2-B: Add UNIQUE constraint to prevent duplicate operation dependencies
--       (same operation_id + predecessor_operation_id pair)
ALTER TABLE operation_dependencies
    ADD CONSTRAINT uq_operation_dependencies
    UNIQUE (operation_id, predecessor_operation_id);

-- Add index on operation_logs.user_id for "filter by user" queries
CREATE INDEX IF NOT EXISTS idx_operation_logs_user ON operation_logs(user_id);
