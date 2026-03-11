-- MES Integration module tables

-- MES sync log table
CREATE TABLE IF NOT EXISTS mes_sync_logs (
    id BIGSERIAL PRIMARY KEY,
    sync_type VARCHAR(50) NOT NULL,
    direction VARCHAR(20) NOT NULL,
    source_system VARCHAR(50),
    target_system VARCHAR(50),
    business_key VARCHAR(200),
    payload TEXT,
    response_body TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    error_message TEXT,
    synced_at TIMESTAMP,
    deleted SMALLINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_mes_sync_logs_sync_type ON mes_sync_logs(sync_type);
CREATE INDEX idx_mes_sync_logs_status ON mes_sync_logs(status);
CREATE INDEX idx_mes_sync_logs_business_key ON mes_sync_logs(business_key);
CREATE INDEX idx_mes_sync_logs_created_at ON mes_sync_logs(created_at);

-- MES work order mapping table (maps external MES IDs to local IDs)
CREATE TABLE IF NOT EXISTS mes_order_mappings (
    id BIGSERIAL PRIMARY KEY,
    local_order_id BIGINT NOT NULL,
    local_order_number VARCHAR(100) NOT NULL,
    mes_order_id VARCHAR(200),
    mes_order_number VARCHAR(200),
    sync_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    last_synced_at TIMESTAMP,
    deleted SMALLINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (local_order_id) REFERENCES work_orders(id)
);

CREATE INDEX idx_mes_order_mappings_local_order_id ON mes_order_mappings(local_order_id);
CREATE INDEX idx_mes_order_mappings_mes_order_id ON mes_order_mappings(mes_order_id);
CREATE INDEX idx_mes_order_mappings_sync_status ON mes_order_mappings(sync_status);
