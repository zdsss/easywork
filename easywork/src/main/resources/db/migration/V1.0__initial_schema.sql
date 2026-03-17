-- XiaoBai Easy WorkOrder System -- Initial Database Schema
-- This is the base schema for a fresh deployment.
-- V1.1-V1.6 incremental migrations are applied on top of this schema.
-- Existing deployments that had V1.1-V1.6 applied manually should set
-- spring.flyway.baseline-on-migrate=true and baseline-version=1.6 in application.yml.

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    employee_number VARCHAR(50) UNIQUE NOT NULL,
    username VARCHAR(100) NOT NULL,
    password VARCHAR(255) NOT NULL,
    real_name VARCHAR(100),
    phone VARCHAR(20),
    email VARCHAR(100),
    role VARCHAR(20) NOT NULL DEFAULT 'WORKER',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    deleted SMALLINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT
);

CREATE INDEX IF NOT EXISTS idx_users_employee_number ON users(employee_number);
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);

-- Teams table
CREATE TABLE IF NOT EXISTS teams (
    id BIGSERIAL PRIMARY KEY,
    team_code VARCHAR(50) UNIQUE NOT NULL,
    team_name VARCHAR(100) NOT NULL,
    description TEXT,
    leader_id BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    deleted SMALLINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    FOREIGN KEY (leader_id) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_teams_team_code ON teams(team_code);
CREATE INDEX IF NOT EXISTS idx_teams_status ON teams(status);

-- Team members table
CREATE TABLE IF NOT EXISTS team_members (
    id BIGSERIAL PRIMARY KEY,
    team_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (team_id) REFERENCES teams(id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    UNIQUE(team_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_team_members_team_id ON team_members(team_id);
CREATE INDEX IF NOT EXISTS idx_team_members_user_id ON team_members(user_id);

-- Devices table
CREATE TABLE IF NOT EXISTS devices (
    id BIGSERIAL PRIMARY KEY,
    device_code VARCHAR(50) UNIQUE NOT NULL,
    device_name VARCHAR(100) NOT NULL,
    device_type VARCHAR(20) NOT NULL DEFAULT 'HANDHELD',
    mac_address VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_login_at TIMESTAMP,
    last_login_user_id BIGINT,
    deleted SMALLINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (last_login_user_id) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_devices_device_code ON devices(device_code);
CREATE INDEX IF NOT EXISTS idx_devices_status ON devices(status);

-- Work orders table (version column added in V1.6)
CREATE TABLE IF NOT EXISTS work_orders (
    id BIGSERIAL PRIMARY KEY,
    order_number VARCHAR(100) UNIQUE NOT NULL,
    order_type VARCHAR(20) NOT NULL,
    product_code VARCHAR(100),
    product_name VARCHAR(200),
    planned_quantity DECIMAL(10,2) NOT NULL,
    completed_quantity DECIMAL(10,2) DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
    priority INTEGER DEFAULT 0,
    planned_start_time TIMESTAMP,
    planned_end_time TIMESTAMP,
    actual_start_time TIMESTAMP,
    actual_end_time TIMESTAMP,
    workshop VARCHAR(100),
    production_line VARCHAR(100),
    notes TEXT,
    deleted SMALLINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT
);

CREATE INDEX IF NOT EXISTS idx_work_orders_order_number ON work_orders(order_number);
CREATE INDEX IF NOT EXISTS idx_work_orders_order_type ON work_orders(order_type);
CREATE INDEX IF NOT EXISTS idx_work_orders_status ON work_orders(status);
CREATE INDEX IF NOT EXISTS idx_work_orders_planned_start_time ON work_orders(planned_start_time);

-- Operations table (version column added in V1.4)
CREATE TABLE IF NOT EXISTS operations (
    id BIGSERIAL PRIMARY KEY,
    work_order_id BIGINT NOT NULL,
    operation_number VARCHAR(100) NOT NULL,
    operation_name VARCHAR(200) NOT NULL,
    operation_type VARCHAR(20) NOT NULL DEFAULT 'PRODUCTION',
    sequence_number INTEGER NOT NULL,
    planned_quantity DECIMAL(10,2) NOT NULL,
    completed_quantity DECIMAL(10,2) DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
    standard_time INTEGER,
    actual_time INTEGER,
    station_code VARCHAR(100),
    station_name VARCHAR(200),
    notes TEXT,
    deleted SMALLINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (work_order_id) REFERENCES work_orders(id),
    UNIQUE(work_order_id, operation_number)
);

CREATE INDEX IF NOT EXISTS idx_operations_work_order_id ON operations(work_order_id);
CREATE INDEX IF NOT EXISTS idx_operations_status ON operations(status);
CREATE INDEX IF NOT EXISTS idx_operations_sequence ON operations(work_order_id, sequence_number);

-- Operation assignments table
CREATE TABLE IF NOT EXISTS operation_assignments (
    id BIGSERIAL PRIMARY KEY,
    operation_id BIGINT NOT NULL,
    assignment_type VARCHAR(20) NOT NULL,
    user_id BIGINT,
    team_id BIGINT,
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (operation_id) REFERENCES operations(id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (team_id) REFERENCES teams(id)
);

CREATE INDEX IF NOT EXISTS idx_operation_assignments_operation_id ON operation_assignments(operation_id);
CREATE INDEX IF NOT EXISTS idx_operation_assignments_user_id ON operation_assignments(user_id);
CREATE INDEX IF NOT EXISTS idx_operation_assignments_team_id ON operation_assignments(team_id);

-- Report records table
CREATE TABLE IF NOT EXISTS report_records (
    id BIGSERIAL PRIMARY KEY,
    operation_id BIGINT NOT NULL,
    work_order_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    device_id BIGINT,
    reported_quantity DECIMAL(10,2) NOT NULL,
    qualified_quantity DECIMAL(10,2),
    defect_quantity DECIMAL(10,2),
    report_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_undone BOOLEAN DEFAULT FALSE,
    undo_time TIMESTAMP,
    undo_reason TEXT,
    notes TEXT,
    deleted SMALLINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (operation_id) REFERENCES operations(id),
    FOREIGN KEY (work_order_id) REFERENCES work_orders(id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (device_id) REFERENCES devices(id)
);

CREATE INDEX IF NOT EXISTS idx_report_records_operation_id ON report_records(operation_id);
CREATE INDEX IF NOT EXISTS idx_report_records_work_order_id ON report_records(work_order_id);
CREATE INDEX IF NOT EXISTS idx_report_records_user_id ON report_records(user_id);
CREATE INDEX IF NOT EXISTS idx_report_records_report_time ON report_records(report_time);

-- Inspection records table
CREATE TABLE IF NOT EXISTS inspection_records (
    id BIGSERIAL PRIMARY KEY,
    work_order_id BIGINT NOT NULL,
    operation_id BIGINT,
    inspector_id BIGINT,
    inspection_type VARCHAR(20) NOT NULL,
    inspection_result VARCHAR(30),
    inspected_quantity DECIMAL(10,2),
    qualified_quantity DECIMAL(10,2),
    defect_quantity DECIMAL(10,2),
    defect_reason TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'NOT_INSPECTED',
    inspection_time TIMESTAMP,
    notes TEXT,
    deleted SMALLINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (work_order_id) REFERENCES work_orders(id),
    FOREIGN KEY (operation_id) REFERENCES operations(id),
    FOREIGN KEY (inspector_id) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_inspection_records_work_order_id ON inspection_records(work_order_id);
CREATE INDEX IF NOT EXISTS idx_inspection_records_operation_id ON inspection_records(operation_id);
CREATE INDEX IF NOT EXISTS idx_inspection_records_status ON inspection_records(status);

-- Call records table
CREATE TABLE IF NOT EXISTS call_records (
    id BIGSERIAL PRIMARY KEY,
    work_order_id BIGINT NOT NULL,
    operation_id BIGINT,
    call_type VARCHAR(20) NOT NULL,
    caller_id BIGINT NOT NULL,
    handler_id BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'NOT_HANDLED',
    call_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    handle_time TIMESTAMP,
    complete_time TIMESTAMP,
    description TEXT,
    handle_result TEXT,
    notes TEXT,
    deleted SMALLINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (work_order_id) REFERENCES work_orders(id),
    FOREIGN KEY (operation_id) REFERENCES operations(id),
    FOREIGN KEY (caller_id) REFERENCES users(id),
    FOREIGN KEY (handler_id) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_call_records_work_order_id ON call_records(work_order_id);
CREATE INDEX IF NOT EXISTS idx_call_records_call_type ON call_records(call_type);
CREATE INDEX IF NOT EXISTS idx_call_records_status ON call_records(status);
CREATE INDEX IF NOT EXISTS idx_call_records_call_time ON call_records(call_time);

-- MES Integration tables
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

CREATE INDEX IF NOT EXISTS idx_mes_sync_logs_sync_type ON mes_sync_logs(sync_type);
CREATE INDEX IF NOT EXISTS idx_mes_sync_logs_status ON mes_sync_logs(status);
CREATE INDEX IF NOT EXISTS idx_mes_sync_logs_business_key ON mes_sync_logs(business_key);
CREATE INDEX IF NOT EXISTS idx_mes_sync_logs_created_at ON mes_sync_logs(created_at);

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

CREATE INDEX IF NOT EXISTS idx_mes_order_mappings_local_order_id ON mes_order_mappings(local_order_id);
CREATE INDEX IF NOT EXISTS idx_mes_order_mappings_mes_order_id ON mes_order_mappings(mes_order_id);
CREATE INDEX IF NOT EXISTS idx_mes_order_mappings_sync_status ON mes_order_mappings(sync_status);

-- Default admin user (password: admin123)
INSERT INTO users (employee_number, username, password, real_name, role, status)
VALUES ('ADMIN001', 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'System Admin', 'ADMIN', 'ACTIVE')
ON CONFLICT (employee_number) DO NOTHING;
