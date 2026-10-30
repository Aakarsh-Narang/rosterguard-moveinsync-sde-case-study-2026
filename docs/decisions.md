# Architecture & Design Decisions (ADR) - RosterGuard

## ADR-001: Modular Monolith vs Microservices
- **Decision**: Single Spring Boot application with clean domain boundaries (`ingestion`, `idempotency`, `ownership`, `reconciliation`, `outbox`, `deactivation`).
- **Rationale**: Eliminates distributed transaction latency and network partition failure modes while guaranteeing strict atomicity between HR persistence and outbox event logging.

## ADR-002: Idempotency Key & Canonical Hashing
- **Decision**: Composite idempotency key = `(tenant_id, source_system, employee_id, source_version)` backed by a unique PostgreSQL constraint, paired with SHA-256 canonical hashing of HR-owned fields.
- **Rule**:
  1. `incoming_version > stored_version`: APPLY.
  2. `incoming_version < stored_version`: STALE (audit & discard).
  3. `incoming_version == stored_version && incoming_hash == stored_hash`: DUPLICATE (idempotent no-op).
  4. `incoming_version == stored_version && incoming_hash != stored_hash`: VERSION_CONFLICT (reject with alert).

## ADR-003: Strict Field Ownership Separation
- **Decision**: Physical schema separation between HR-owned attributes in `employees` table and transport-owned attributes in `employee_transport_profiles` table.
- **HR Fields**: `name`, `email`, `manager_id`, `office_code`, `source_version`, `active`.
- **Transport Fields**: `preferred_pickup_point`, `special_assistance_needs`.
- **Rationale**: Schema-level separation makes accidental overwrites via generic ORM mapping physically impossible.

## ADR-004: Transactional Outbox with SKIP LOCKED
- **Decision**: Write domain changes, idempotency receipt, and outbox event in the same ACID transaction. An asynchronous worker claims pending events using `SELECT ... FOR UPDATE SKIP LOCKED` with exponential backoff and leasing.
- **Rationale**: Guarantees At-Least-Once downstream delivery without dual-write inconsistency or database deadlocks.

## ADR-005: Streaming Reconciliation & Safety Brake
- **Decision**: Nightly 200,000 CSV records are parsed via streaming Jackson/OpenCSV into 1,000-row batch inserts into PostgreSQL `reconciliation_staging`. The diff runs database-side via indexed `LEFT JOIN` and hash comparison.
- **Safety Brake**: If `missing_active / total_existing_active >= 0.30` (configurable), automatic deactivations are aborted, status marked `BLOCKED_REQUIRES_APPROVAL`, and human operator intervention is mandated.
