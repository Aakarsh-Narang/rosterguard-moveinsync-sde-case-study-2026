# RosterGuard — System Design & Architecture Specification

## 1. System Overview
**RosterGuard** is an idempotent, out-of-order, self-healing HR synchronization engine designed to bridge corporate HR systems and the MoveInSync employee transport fleet.

### Key Architectural Pillars
- **Strict Idempotency & Per-Employee Monotonic Ordering**: Guaranteed via composite keys and canonical SHA-256 payload hashing.
- **Physical Field Ownership**: Schema-level separation prevents HR updates from corrupting transport preferences.
- **Transactional Outbox with Lease Recovery**: Single-transaction database changes + durable outbox events with `FOR UPDATE SKIP LOCKED`.
- **Streaming Nightly Reconciliation with Safety Brake**: Bounded-memory CSV diff of 200,000 employees with configurable circuit breaker preventing catastrophic mass deactivations.
- **Tenant Isolation & RBAC**: Tenant context enforced from JWT authentication token.

---

## 2. Architecture Diagram

```mermaid
graph TD
    HR[HR External System / Workday] -->|HTTPS REST| GW[RosterGuard API Gateway]
    CSV[Nightly CSV Snapshot 200K Rows] -->|Streaming Upload| GW

    subgraph Security_Context[Security & Tenant Context]
        GW -->|Verify JWT Principal| TC[TenantContext]
    end

    subgraph Core_Engine[RosterGuard Core Engine]
        TC -->|Validate Boundary| VAL[EventValidationService]
        VAL -->|Check Idempotency & Order| IDEM[Idempotency & Ordering Engine]
        
        IDEM -->|1. Update HR Fields| DB_EMP[(PostgreSQL: employees)]
        IDEM -->|2. Record Receipt| DB_IDEM[(PostgreSQL: idempotency_receipts)]
        IDEM -->|3. Insert Outbox Event| DB_OUT[(PostgreSQL: outbox_events)]
    end

    subgraph Ownership_Isolation[Field Ownership]
        ADMIN[Transport Admin] -->|Update Pickup/Assistance| DB_PROF[(PostgreSQL: employee_transport_profiles)]
        DB_EMP -.->|FK employee_id| DB_PROF
    end

    subgraph Reconciliation_Engine[Streaming Reconciliation]
        CSV -->|OpenCSV / Jackson Stream 1K Batches| STG[(PostgreSQL: reconciliation_staging)]
        STG -->|DB-Side SQL Diff & Hash Compare| DIFF[Diff & Hash Matcher]
        DIFF -->|Missing Ratio >= 30%| BRAKE[Safety Brake Circuit Breaker]
        BRAKE -->|Hold & Alert| OP[Human Operator Approval]
        BRAKE -->|Within Threshold| IDEM
    end

    subgraph Async_Event_Bus[Transactional Outbox Poller]
        DB_OUT -->|FOR UPDATE SKIP LOCKED| POL[OutboxPollingWorker]
        POL -->|At-Least-Once Dispatch| PUB[EventPublisher]
        PUB -->|Consume Event| DEACT[DeactivationConsumer]
        DEACT -->|Cancel Future Bookings & Free Seats| BOOK[(PostgreSQL: cab_bookings)]
        DEACT -->|Deduplicate Event ID| DEDUP[(PostgreSQL: consumed_events)]
    end
```

---

## 3. Event Processing Sequence Diagram

```mermaid
sequenceDiagram
    autonumber
    actor HR as HR System
    participant API as HrEventController
    participant VAL as EventValidationService
    participant SVC as EmployeeIngestionService
    participant IDEM as IdempotencyService
    participant DB as PostgreSQL (ACID Tx)
    participant WORKER as OutboxPollingWorker
    participant CONS as DeactivationConsumer

    HR->>API: POST /api/v1/hr/events (v7 Transfer)
    API->>VAL: Validate fields (email, phone, office)
    VAL-->>API: Validated (No PII leak)
    API->>SVC: Ingest Event (v7)
    SVC->>DB: BEGIN TRANSACTION
    SVC->>IDEM: Check Idempotency Key (tenant+source+emp+v7)
    
    alt Version > Stored Version
        IDEM->>DB: UPDATE employees (name, office, v7, hash)
        IDEM->>DB: INSERT idempotency_receipts (v7, APPLIED)
        IDEM->>DB: INSERT outbox_events (EmployeeTransferred, PENDING)
        DB-->>SVC: Commit Successful
        SVC-->>API: Result: APPLIED
    else Version < Stored Version
        IDEM->>DB: INSERT idempotency_receipts (v6, STALE)
        DB-->>SVC: Commit Audit Record
        SVC-->>API: Result: STALE (No-op)
    else Version == Stored Version & Same Hash
        SVC-->>API: Result: DUPLICATE (No-op)
    else Version == Stored Version & Different Hash
        SVC-->>API: Result: VERSION_CONFLICT (Rejected)
    end

    API-->>HR: HTTP 200 {status: APPLIED/STALE/DUPLICATE}

    Note over WORKER,DB: Asynchronous Outbox Polling
    WORKER->>DB: SELECT * FROM outbox_events WHERE status='PENDING' FOR UPDATE SKIP LOCKED
    WORKER->>CONS: Dispatch Event (EmployeeDeactivated / Transferred)
    CONS->>DB: Cancel future cab_bookings (pickup_time > NOW)
    CONS->>DB: Record consumed_events(event_id)
    WORKER->>DB: UPDATE outbox_events SET status='PUBLISHED', published_at=NOW()
```

---

## 4. Database Consistency Decisions
1. **Isolation Level**: Read Committed with explicit pessimistic row locks (`SELECT ... FOR UPDATE`) during concurrent employee mutations to guarantee zero lost updates or version rollbacks.
2. **Single Transaction Boundary**: Employee update, idempotency receipt, and outbox event are committed atomically in one database transaction.
3. **Pessimistic Skip-Locked Work Queues**: Outbox worker polls using `FOR UPDATE SKIP LOCKED`, preventing database worker contention and deadlock states.

---

## 5. Failure Modes & Recovery Matrix

| Failure Mode | Root Cause / Trigger | User / Caller Experience | System Self-Healing & Recovery |
|---|---|---|---|
| **Out-of-Order Delivery** | Network jitter; v7 arrives before v6. | Caller receives `STALE` for v6. v7 state remains active. | Version comparison rejects v6; audit receipt stored in `idempotency_receipts`. |
| **Duplicate Events** | Source retry after timeout. | Caller receives `DUPLICATE` (HTTP 200). | Zero write to `employees`, idempotency key returns cached receipt. |
| **Version Payload Conflict** | Source bug sends two different payloads for same version. | Caller receives `VERSION_CONFLICT` (HTTP 409). | Transaction rolled back; recorded in security/audit log. |
| **Crash Before Outbox Dispatch** | JVM kills worker between DB commit and publication. | HR caller already received success (HTTP 200). | Next polling worker lease claims pending outbox row and publishes it. At-least-once guarantee. |
| **Truncated Nightly File** | Network drop cuts 200K CSV to 120K rows. | Reconciliation marked `BLOCKED_REQUIRES_APPROVAL`. | Safety brake aborts mass deactivations; operator reviews diff and approves or rejects run. |
| **Transport Preference Collision** | HR event attempts to update pickup point. | HR event applied; pickup point unchanged. | HR ingestion writes exclusively to `employees`; transport table remains untouched. |
| **Cross-Tenant Breach** | Caller supplies Tenant A token with Tenant B employeeId. | HTTP 404 / 403 Forbidden without leaking existence. | Queries enforce `WHERE tenant_id = :authenticatedTenantId`. |
