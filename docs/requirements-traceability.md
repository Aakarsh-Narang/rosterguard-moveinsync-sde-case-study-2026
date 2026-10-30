# Requirements Traceability Matrix - RosterGuard

| ID | Requirement Category | Case Study Specification | Architectural Component | Verification / Test |
|---|---|---|---|---|
| R-01 | Ingest & Validation | Real-time & batch events (`CREATE`, `UPDATE`, `DEACTIVATE`), record-level errors, boundary checks (phone, office, email). Partial batch success. | `EventValidationService`, `HrEventController` | `ValidationTest`, `PartialBatchTest` |
| R-02 | Per-Employee Order | Apply changes per employee in version order. Version 7 before Version 6 preserves Version 7. Stale updates ignored. | `VersionManagementPolicy`, `IdempotencyService` | `OutOfOrderTest`, `StaleEventTest` |
| R-03 | Idempotency | Composite key `(tenant, source, emp, version)`. Duplicate event is a safe no-op. Version conflict detection. | `IdempotencyService`, `idempotency_receipts` table | `IdempotencyTest`, `VersionConflictTest` |
| R-04 | Field Ownership | HR owns name, email, manager, office. Transport owns pickup point, special assistance. HR never overwrites transport fields. | Separate `employees` & `employee_transport_profiles` tables | `FieldOwnershipTest` |
| R-05 | Transactional Outbox | Outbox pattern in same DB transaction. Worker uses `FOR UPDATE SKIP LOCKED`. At-least-once delivery. Crash resilience. | `OutboxService`, `OutboxPollingWorker`, `EventPublisher` | `OutboxCrashRecoveryTest` |
| R-06 | Deactivation Side Effects | Leaving employees get future bookings cancelled & seats released. Past bookings untouched. Consumer deduplication. | `DeactivationConsumer`, `cab_bookings`, `consumed_events` | `DeactivationLifecycleTest`, `IdempotentConsumerTest` |
| R-07 | Nightly Reconciliation | Stream 200,000 CSV rows, bounded memory, hash per record, batch staging, DB-side diff, safe missing employee detection. | `ReconciliationService`, `ReconciliationController` | `StreamingDiffBenchmarkTest` |
| R-08 | Safety Brake | Abort auto-deactivation if >= 30% missing. Alert operator, require explicit approval. | `SafetyBrakePolicy`, `reconciliation_runs` | `SafetyBrakeThresholdTest`, `OperatorApprovalTest` |
| R-09 | Multi-Tenancy & Security | JWT auth, RBAC (`HR_SYNC`, `ADMIN`, `OPERATOR`, `EMPLOYEE`), tenant isolation via verified principal. | `JwtAuthenticationFilter`, `TenantContext` | `TenantIsolationTest`, `RbacAuthorizationTest` |
| R-10 | Observability & Caching | Micrometer counters, PII-free structured logging, correlation IDs, Caffeine cache for office reference data. | `ObservabilityConfig`, `ReferenceDataCache` | `MetricsExportTest`, `CacheStalenessTest` |
