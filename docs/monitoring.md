# RosterGuard — Operational Monitoring & Metrics Guide

This document describes the operational observability instrumentation implemented in RosterGuard via Micrometer and exposed through the Prometheus actuator endpoint at `/actuator/prometheus`.

---

## 1. Metrics Catalog

### Event Ingestion Pipeline

| Metric Name | Type | Description | Operational Purpose | Sample Prometheus / PromQL Query |
| :--- | :--- | :--- | :--- | :--- |
| `hr_events_received_total` | Counter | Total HR webhook/API events received by the ingestion boundary. | Ingestion traffic volume tracking and rate calculation. | `rate(hr_events_received_total[5m])` |
| `hr_events_applied_total` | Counter | Total HR events successfully validated, sequenced, and committed to PostgreSQL. | Service write throughput and business progress. | `rate(hr_events_applied_total[5m])` |
| `hr_events_duplicate_total` | Counter | Events discarded due to identical payload SHA-256 hash (canonical idempotency). | Detection of upstream duplicate delivery or replay storms. | `rate(hr_events_duplicate_total[5m])` |
| `hr_events_stale_total` | Counter | Events discarded because `source_version <= existing_version` (out-of-order handling). | Quantifying out-of-order delivery frequency from HRIS systems (e.g. Workday). | `rate(hr_events_stale_total[5m])` |
| `hr_events_rejected_total` | Counter | Events rejected due to payload validation errors, malformed JSON, or missing mandatory fields. | Data quality monitoring at the tenant boundary. | `rate(hr_events_rejected_total[5m])` |
| `hr_event_processing_duration_seconds` | Timer / Histogram | Latency distribution of end-to-end ingestion processing (validation, hashing, locking, version check, persistence, outbox write). | SLA & SLO verification (p50, p95, p99 latency). | `histogram_quantile(0.99, rate(hr_event_processing_duration_seconds_bucket[5m]))` |

### Transactional Outbox Pipeline

| Metric Name | Type | Description | Operational Purpose | Sample Prometheus / PromQL Query |
| :--- | :--- | :--- | :--- | :--- |
| `outbox_pending_count` | Gauge | Current number of unpropagated outbox records (`status = PENDING`) in PostgreSQL. | Queue depth and downstream lag alerting. | `outbox_pending_count` |
| `outbox_retry_total` | Counter | Total retry attempts for failed outbox publications. | Detecting downstream transport or consumer unhealthiness. | `rate(outbox_retry_total[5m])` |
| `outbox_oldest_pending_age_seconds` | Gauge | Age in seconds of the oldest unpropagated outbox record. | Detection of stuck or poison-pill outbox messages. | `outbox_oldest_pending_age_seconds` |
| `outbox_publish_duration_seconds` | Timer / Histogram | Latency distribution of individual outbox message delivery to consumers. | Downstream consumer responsiveness. | `histogram_quantile(0.95, rate(outbox_publish_duration_seconds_bucket[5m]))` |

### Nightly Reconciliation Engine

| Metric Name | Type | Description | Operational Purpose | Sample Prometheus / PromQL Query |
| :--- | :--- | :--- | :--- | :--- |
| `reconciliation_rows_processed_total` | Counter | Cumulative rows streamed and parsed from HRIS snapshot CSV files. | Batch processing throughput. | `rate(reconciliation_rows_processed_total[5m])` |
| `reconciliation_invalid_rows_total` | Counter | Total malformed or schema-violating CSV rows encountered during streaming. | Input CSV quality and parser integrity. | `reconciliation_invalid_rows_total` |
| `reconciliation_duration_seconds` | Timer / Histogram | Total elapsed execution time of nightly reconciliation runs. | Nightly batch job runtime SLA tracking. | `reconciliation_duration_seconds_sum / reconciliation_duration_seconds_count` |
| `reconciliation_missing_ratio` | Gauge | Ratio of currently active database employees missing from the snapshot (`missing / active_before`). | Safety-brake metric: threshold comparison. | `reconciliation_missing_ratio` |
| `safety_brake_triggered_total` | Counter | Total reconciliation runs halted due to missing ratio exceeding threshold (default 30%). | Incident escalation: catastrophic bulk deactivation prevented. | `increase(safety_brake_triggered_total[1h])` |

### Reference Data Caffeine Cache

| Metric Name | Type | Description | Operational Purpose | Sample Prometheus / PromQL Query |
| :--- | :--- | :--- | :--- | :--- |
| `cache_hits_total` | Gauge | Cumulative cache hits for office reference data. | Cache efficiency evaluation. | `rate(cache_hits_total[5m])` |
| `cache_misses_total` | Gauge | Cumulative cache misses requiring database lookup. | Sizing validation and cache coldness detection. | `rate(cache_misses_total[5m])` |
| `cache_evictions_total` | Gauge | Cumulative cache entries evicted due to TTL (60s) or size limit (10,000). | Cache churn and memory pressure tracking. | `rate(cache_evictions_total[5m])` |
| `cache_size` | Gauge | Current number of entries held in Caffeine in-memory cache. | Cache utilization tracking. | `cache_size` |

### Downstream Side-Effect Consumers

| Metric Name | Type | Description | Operational Purpose | Sample Prometheus / PromQL Query |
| :--- | :--- | :--- | :--- | :--- |
| `cab_bookings_cancelled_total` | Counter | Downstream cab bookings cancelled upon employee deactivation. | Audit and downstream operational cost savings. | `increase(cab_bookings_cancelled_total[1h])` |
| `seat_releases_total` | Counter | Office workstation seats released upon employee deactivation. | Real-time facility capacity recovery tracking. | `increase(seat_releases_total[1h])` |
| `consumer_duplicate_events_total` | Counter | Duplicate outbox events detected and safely ignored by downstream idempotency guards. | Downstream at-least-once deduplication verification. | `rate(consumer_duplicate_events_total[5m])` |

---

## 2. Production Alert Rules & Queries

### High Ingestion Rejection Rate
- **Condition**: Rejections exceed 5% of incoming events over 5 minutes.
- **PromQL**:
  ```promql
  (rate(hr_events_rejected_total[5m]) / (rate(hr_events_received_total[5m]) > 0)) * 100 > 5
  ```
- **Severity**: Warning
- **Remediation**: Inspect recent validation error logs for upstream payload schema divergence or tenant misconfiguration.

### HR Event Ingestion P99 Latency Breach
- **Condition**: 99th percentile ingestion latency exceeds 100 milliseconds over 5 minutes.
- **PromQL**:
  ```promql
  histogram_quantile(0.99, rate(hr_event_processing_duration_seconds_bucket[5m])) > 0.100
  ```
- **Severity**: Critical
- **Remediation**: Inspect PostgreSQL row lock contention (`SELECT FOR UPDATE` on employee records) or high connection pool utilization.

### Transactional Outbox Lag / Queue Depth
- **Condition**: Pending outbox messages exceed 100 entries for more than 2 minutes.
- **PromQL**:
  ```promql
  outbox_pending_count > 100
  ```
- **Severity**: Warning
- **Remediation**: Check worker polling interval, downstream consumer latency, or database transaction duration.

### Outbox Stuck Message (Oldest Pending Age)
- **Condition**: Oldest unpropagated outbox message exceeds 60 seconds.
- **PromQL**:
  ```promql
  outbox_oldest_pending_age_seconds > 60
  ```
- **Severity**: Critical
- **Remediation**: Check outbox poller thread health, deadlock in polling loop, or dead-letter retries exceeding limits.

### Reconciliation Execution Time Exceeded
- **Condition**: 200K nightly reconciliation execution duration exceeds 60 seconds.
- **PromQL**:
  ```promql
  reconciliation_duration_seconds_max > 60
  ```
- **Severity**: Warning
- **Remediation**: Check PostgreSQL temp tablespace I/O, JDBC batch sizes, or network transmission bottlenecks during CSV streaming.

### Reconciliation Safety-Brake Activation
- **Condition**: Safety brake triggered at least once in the past 1 hour.
- **PromQL**:
  ```promql
  increase(safety_brake_triggered_total[1h]) > 0
  ```
- **Severity**: Critical (PagerDuty Alert)
- **Remediation**: Automatic employee deactivations are HALTED. Run status persisted as `BLOCKED_REQUIRES_APPROVAL`. Operator must inspect the sample CSV discrepancies and explicitly POST to `/api/v1/reconciliation/{runId}/approve` if valid, or reject the corrupted snapshot.

---

## 3. Reference Data Cache Policy & Staleness Contract

RosterGuard employs an in-memory Caffeine cache (`ReferenceDataCache`) for office and location reference lookup:

| Attribute | Specification | Rationale |
| :--- | :--- | :--- |
| **Max Cache Size** | 10,000 entries | Prevents JVM heap exhaustion while comfortably caching all global enterprise offices. |
| **Time-To-Live (TTL)** | 60 seconds (`expireAfterWrite`) | Limits stale reference data window to maximum 60 seconds without complex pub-sub cache syncing. |
| **Eviction Policy** | Window TinyLFU | Evicts infrequently accessed office entries under memory pressure. |
| **Explicit Invalidation** | `invalidate(tenantId, officeCode)` and `clear()` | Triggered immediately upon administrative office mutations. |
| **Staleness Contract** | Maximum 60 seconds normal stale window | Office reference mutations (e.g., location timezone change) propagate within 60s. |
| **Fallback Behavior** | Direct PostgreSQL read on cache miss | Ensures high availability; cache failure gracefully falls back to database queries. |
| **Critical Exclusion** | **NEVER cache employee version state** | Employee versions and hashes are strictly read from PostgreSQL row locks to guarantee linearizable concurrency. |
