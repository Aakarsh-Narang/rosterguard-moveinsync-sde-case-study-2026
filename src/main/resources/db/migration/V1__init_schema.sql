-- Flyway Database Migration V1: Normalized Schema for RosterGuard

CREATE TABLE IF NOT EXISTS tenants (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenants(id),
    username VARCHAR(128) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(64) NOT NULL,
    employee_id VARCHAR(64),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_users_tenant_username UNIQUE (tenant_id, username)
);

CREATE TABLE IF NOT EXISTS offices (
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenants(id),
    office_code VARCHAR(64) NOT NULL,
    office_name VARCHAR(255) NOT NULL,
    PRIMARY KEY (tenant_id, office_code)
);

CREATE TABLE IF NOT EXISTS employees (
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenants(id),
    employee_id VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(32),
    manager_id VARCHAR(64),
    office_code VARCHAR(64) NOT NULL,
    source_system VARCHAR(64) NOT NULL,
    source_version BIGINT NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    last_hr_update_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (tenant_id, employee_id)
);

CREATE INDEX IF NOT EXISTS idx_employees_tenant_active ON employees(tenant_id, active);

CREATE TABLE IF NOT EXISTS employee_transport_profiles (
    tenant_id VARCHAR(64) NOT NULL,
    employee_id VARCHAR(64) NOT NULL,
    preferred_pickup_point VARCHAR(255) NOT NULL,
    special_assistance_needs VARCHAR(255),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (tenant_id, employee_id),
    CONSTRAINT fk_profile_employee FOREIGN KEY (tenant_id, employee_id)
        REFERENCES employees(tenant_id, employee_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS idempotency_receipts (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenants(id),
    source_system VARCHAR(64) NOT NULL,
    employee_id VARCHAR(64) NOT NULL,
    source_version BIGINT NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    processing_outcome VARCHAR(32) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_idempotency_receipt UNIQUE (tenant_id, source_system, employee_id, source_version)
);

CREATE INDEX IF NOT EXISTS idx_idempotency_lookup 
    ON idempotency_receipts(tenant_id, employee_id, source_version);

CREATE TABLE IF NOT EXISTS outbox_events (
    event_id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenants(id),
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    last_error_code VARCHAR(64)
);

CREATE INDEX IF NOT EXISTS idx_outbox_pending_poll 
    ON outbox_events(status, next_attempt_at);

CREATE TABLE IF NOT EXISTS reconciliation_runs (
    run_id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenants(id),
    snapshot_generated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    total_rows INT NOT NULL DEFAULT 0,
    valid_rows INT NOT NULL DEFAULT 0,
    invalid_rows INT NOT NULL DEFAULT 0,
    missing_employees INT NOT NULL DEFAULT 0,
    missing_ratio DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    status VARCHAR(32) NOT NULL,
    operator_approval VARCHAR(128),
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS reconciliation_staging (
    run_id VARCHAR(64) NOT NULL REFERENCES reconciliation_runs(run_id) ON DELETE CASCADE,
    tenant_id VARCHAR(64) NOT NULL,
    employee_id VARCHAR(64) NOT NULL,
    source_version BIGINT NOT NULL,
    canonical_hash VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(32),
    manager_id VARCHAR(64),
    office_code VARCHAR(64) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (run_id, tenant_id, employee_id)
);

CREATE INDEX IF NOT EXISTS idx_recon_staging_lookup 
    ON reconciliation_staging(run_id, tenant_id, employee_id);

CREATE TABLE IF NOT EXISTS cab_bookings (
    booking_id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenants(id),
    employee_id VARCHAR(64) NOT NULL,
    pickup_time TIMESTAMP WITH TIME ZONE NOT NULL,
    booking_status VARCHAR(32) NOT NULL,
    seat_id VARCHAR(64),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_cab_bookings_deact_search 
    ON cab_bookings(tenant_id, employee_id, booking_status, pickup_time);

CREATE TABLE IF NOT EXISTS consumed_events (
    consumer_name VARCHAR(64) NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (consumer_name, event_id)
);
