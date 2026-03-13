-- Add rework records table
CREATE TABLE rework_records (
    id BIGSERIAL PRIMARY KEY,
    work_order_id BIGINT NOT NULL,
    original_operation_id BIGINT NOT NULL,
    rework_operation_id BIGINT NOT NULL,
    rework_quantity DECIMAL(10,2) NOT NULL,
    rework_reason TEXT,
    rework_times INTEGER DEFAULT 1,
    deleted INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (work_order_id) REFERENCES work_orders(id),
    FOREIGN KEY (original_operation_id) REFERENCES operations(id),
    FOREIGN KEY (rework_operation_id) REFERENCES operations(id)
);

CREATE INDEX idx_rework_records_work_order ON rework_records(work_order_id);
