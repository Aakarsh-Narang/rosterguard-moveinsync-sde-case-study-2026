# RosterGuard — Comprehensive Failure Modes & Recovery Analysis

This document catalogs critical failure modes in the MoveInSync HR Synchronization Engine, detailing root causes, impact, automatic recovery mechanisms, and operator runbooks.

---

## 1. Network Jitter & Out-of-Order Delivery
- **Scenario**: Version 7 (transfer to Hyderabad) is transmitted over an expedited network path and arrives before Version 6 (shift change in Bangalore).
- **Failure Impact**: In naive systems, Version 6 overwrites Version 7, leaving the employee assigned to the wrong office.
- **RosterGuard Mitigation**:
  - `VersionManagementPolicy` checks `incomingVersion > storedVersion`.
  - Database-level guard: `UPDATE employees ... WHERE source_version < incomingVersion`.
  - Version 6 is rejected with outcome `STALE`.
  - An audit receipt is recorded in `idempotency_receipts`.
  - Employee remains in Version 7 in Hyderabad.
- **Automated Test Proving Recovery**: `IdempotencyAndOrderingIntegrationTest.outOfOrderEvents_rejectsStaleVersion`, `ConcurrentEventIngestionTest.concurrentOutOfOrderRequests_neverRegressVersion`.

---

## 2. Duplicate Delivery from Upstream Retries
- **Scenario**: Workday or API gateway timeouts trigger an automatic retry of Version 7.
- **Failure Impact**: Redundant database updates, wasted CPU, duplicate outbox events.
- **RosterGuard Mitigation**:
  - Idempotency key `(tenant_id, source_system, employee_id, source_version)` backed by a unique PostgreSQL constraint.
  - Canonical SHA-256 payload hash is checked. If version and hash match, returns `DUPLICATE` (HTTP 200) without writing to `employees` or creating outbox events.
- **Automated Test Proving Recovery**: `IdempotencyAndOrderingIntegrationTest.replayedIdenticalEvent_returnsDuplicateReceipt`, `ConcurrentEventIngestionTest.concurrentIdenticalRequests_produceOneApplication`.

---

## 3. Version Payload Conflict (Divergent Payloads)
- **Scenario**: Source system sends two contradictory payloads under the identical version number.
- **Failure Impact**: Silent data corruption or race condition.
- **RosterGuard Mitigation**:
  - Canonical SHA-256 hash differs from stored receipt hash for the same version.
  - System rejects the update with `VERSION_CONFLICT` (HTTP 409).
  - An operational alert is recorded in structured logs without leaking PII.
- **Automated Test Proving Recovery**: `IdempotencyAndOrderingIntegrationTest.divergentPayloadSameVersion_returnsConflict`, `VersionManagementPolicyTest.evaluate_sameVersionDifferentPayload_returnsConflict`.

---

## 4. Process Crash Between Database Write & Outbox Event Publication
- **Scenario**: PostgreSQL transaction commits employee state, idempotency receipt, and outbox event (`PENDING`). The JVM abruptly crashes or loses power before the event is dispatched to the downstream message broker/consumer.
- **Failure Impact**: In dual-write architectures, the event is permanently lost, leaving downstream systems out of sync.
- **RosterGuard Mitigation**:
  - The outbox event is durably committed in the same ACID transaction as the employee update.
  - Upon restart, `OutboxPollingWorker` polls pending events using `FOR UPDATE SKIP LOCKED`.
  - The pending event is claimed, dispatched, and marked `PUBLISHED`.
  - Downstream side effects are executed reliably with at-least-once delivery.
- **Automated Test Proving Recovery**: `OutboxCrashRecoveryTest.crashBetweenDatabaseWriteAndPublish_recoversPendingEvents`.

---

## 5. Truncated Nightly Snapshot (Circuit Breaker / Safety Brake)
- **Scenario**: A 200,000-row nightly employee export is severed at 120,000 rows due to an upstream network timeout or disk failure.
- **Failure Impact**: A naive diff engine would consider the 80,000 omitted records as terminated employees and trigger mass deactivations, cancelling 80,000 cab bookings and stranding commuters.
- **RosterGuard Mitigation**:
  - Formula: `missingRatio = missingActiveEmployees / existingActiveEmployees` ($80,000 / 200,000 = 40\%$).
  - Configured threshold: `roster.reconciliation.missing-threshold=0.30` (30%).
  - Because $40\% \ge 30\%$, the **Safety Brake** triggers:
    1. Automatic mass deactivations are aborted immediately.
    2. Zero employees are deactivated.
    3. Run status is set to `BLOCKED_REQUIRES_APPROVAL`.
    4. Metric `safety_brake_triggered_total` is incremented.
    5. Discrepancy is logged and presented via `GET /api/v1/reconciliations/{runId}`.
    6. Only an authenticated `OPERATOR` can review and execute deactivations via `POST /api/v1/reconciliations/{runId}/approve`.
- **Automated Test Proving Recovery**: `ReconciliationSafetyBrakeIntegrationTest.missingRatioExceedsThreshold_blocksRunUntilApproved`, Live Demo E.

---

## 6. Downstream Message Replay / Duplicate Consumption
- **Scenario**: An outbox worker or Kafka consumer re-delivers an `EmployeeDeactivated` event.
- **Failure Impact**: Attempting to cancel already-cancelled bookings, double-freeing seats, or creating inconsistent audit trails.
- **RosterGuard Mitigation**:
  - `DeactivationConsumer` maintains a dedicated `consumed_events` table with primary key `(consumer_name, event_id)`.
  - If the event was previously processed, the consumer returns immediately without re-executing database mutations.
- **Automated Test Proving Recovery**: `DeactivationSideEffectsIntegrationTest.deactivationEvent_cancelsFutureBookingsAndPreservesPast`, Live Demo F & G.

---

## 7. Cross-Tenant Data Access Attempt
- **Scenario**: A compromised API client from Tenant A attempts to access or mutate an employee record belonging to Tenant B.
- **Failure Impact**: Data breach, PII leakage, regulatory violation.
- **RosterGuard Mitigation**:
  - Tenant ID is extracted strictly from the cryptographically verified JWT token (`TenantContext`), never from client-controlled request parameters.
  - All database queries enforce `WHERE tenant_id = :authenticatedTenantId`.
  - Cross-tenant queries return HTTP 404 / 403 without revealing whether the record exists in another tenant.
- **Automated Test Proving Recovery**: `TenantIsolationIntegrationTest.tenantA_cannotAccessTenantBEmployees`.
