-- Add operation dependencies table for complex workflow routing
CREATE TABLE operation_dependencies (
    id BIGSERIAL PRIMARY KEY,
    operation_id BIGINT NOT NULL,
    predecessor_operation_id BIGINT NOT NULL,
    dependency_type VARCHAR(20) NOT NULL CHECK (dependency_type IN ('SERIAL', 'PARALLEL', 'CONDITIONAL')),
    condition_expression TEXT,
    deleted INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (operation_id) REFERENCES operations(id),
    FOREIGN KEY (predecessor_operation_id) REFERENCES operations(id)
);

CREATE INDEX idx_operation_dependencies_operation ON operation_dependencies(operation_id);
CREATE INDEX idx_operation_dependencies_predecessor ON operation_dependencies(predecessor_operation_id);
