# RosterGuard — Project Submission Guide
**MoveInSync Case Study 5: HR Roster Sync (Idempotent, Out-of-Order, Self-Healing)**  
**Assessment Target:** Advanced SDE Placement Assessment  
**Author:** Aakarsh-Narang (<184413376+Aakarsh-Narang@users.noreply.github.com>)  
**Version:** 1.0.0-RELEASE

---

## 1. Project Identity

- **Project Title:** RosterGuard — An Idempotent, Out-of-Order, Self-Healing HR Synchronization Engine
- **Target Role:** Advanced Software Development Engineer (Backend / Distributed Systems)
- **Primary Domain:** Enterprise HR Roster Ingestion & Automated Commute Fleet Fleet Coordination
- **Core Repository:** Java 21 LTS, Spring Boot 3.3.4, PostgreSQL 16/18, Flyway, Caffeine, Micrometer

---

## 2. Repository & Architectural Structure

The codebase is organized into clean domain and infrastructure boundaries:

```
rosterguard/
├── src/main/java/com/rosterguard/
│   ├── application/
│   │   ├── consumer/           # Downstream consumers (DeactivationConsumer)
│   │   ├── dto/                # Request/Response data transfer objects
│   │   └── service/            # Core business workflows (Ingestion, Reconciliation, Validation)
│   ├── config/                 # Security, cache, openapi, and database seed configuration
│   ├── domain/
│   │   ├── model/              # Domain models, canonical hashers, idempotency receipts
│   │   └── policy/             # Version management & safety brake circuit breaker policies
│   ├── infrastructure/
│   │   ├── csv/                # Memory-bounded streaming OpenCSV parser
│   │   ├── outbox/             # Transactional outbox polling worker & event publisher
│   │   └── persistence/        # Spring JDBC & JdbcTemplate repositories (Pessimistic row locking)
│   ├── interfaces/rest/        # REST controllers (HrEventController, ReconciliationController, etc.)
│   └── security/               # TenantContext, JwtTokenProvider, and Spring Security filters
├── src/main/resources/
│   ├── db/migration/           # Flyway versioned migration scripts (V1, V2)
│   └── application.yml         # Application configuration with environment variable defaults
├── src/test/java/com/rosterguard/
│   ├── benchmark/              # 200,000-row streaming reconciliation benchmark
│   ├── failure/                # Outbox crash and interrupted lease recovery tests
│   ├── integration/            # Concurrency, idempotency, field ownership, safety brake, tenant isolation
│   └── unit/                   # Canonical hashing, validation, version management policy tests
├── scripts/
│   ├── demo_api.ps1            # Windows PowerShell automated end-to-end demonstration script
│   └── demo_api.bat            # Windows batch wrapper for one-click demonstration
├── docs/
│   ├── SUBMISSION.md           # This comprehensive submission guide
│   ├── demo-script.md          # 3–5 minute presentation and recording guide
│   ├── failure-modes.md        # Comprehensive failure mode, detection & self-healing matrix
│   ├── release-checklist.md    # Complete 4-tier release verification checklist
│   └── requirements-traceability.md # 100% requirements-to-test mapping matrix
├── docker-compose.yml          # Containerized multi-service setup (PostgreSQL 16 + RosterGuard App)
├── Dockerfile                  # Multi-stage optimized Alpine JRE build
├── pom.xml                     # Maven project descriptor (strictly configured for release 21)
└── README.md                   # Complete architectural and operational manual
```

---

## 3. How to Run the System

### Prerequisites
- JDK 21+ installed (Project compiled with `--release 21`).
- PostgreSQL 16+ running locally on port 5432 (or running via Docker Compose).

### Verified Run Commands

#### Step 1: Environment Setup
Copy the example environment configuration:
```bash
cp .env.example .env
```
*(Optionally adjust `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`, or `ROSTER_DEMO_PASSWORD` in `.env`)*

#### Step 2: Build & Automated Test Execution
Run the complete automated test suite including failure-recovery and benchmark tests:
```powershell
.\mvnw.bat clean test
```
*Expected: 23 tests run, 0 failures, 0 errors, 0 skipped.*

#### Step 3: Run Application Locally
Start the Spring Boot backend:
```powershell
.\mvnw.bat spring-boot:run
```
The server will start on `http://localhost:8080`.

#### Step 4: Docker Compose Execution (Alternative)
```bash
docker compose up --build -d
```

---

## 4. How to Demonstrate

The project includes an automated end-to-end demonstration script verifying all 12 operational criteria:

### Execute Demonstration
```cmd
.\scripts\demo_api.bat
```

### Demonstration Scenarios & Verification Order
1. **System Health Check**: Verifies `GET /actuator/health` returns `UP`.
2. **HR Employee Creation**: Creates employee `EMP-DEMO-001` (Version 1) via `POST /api/v1/hr/events`.
3. **Out-of-Order Handling**: Ingests Version 7 (Promoted to Engineering Lead), followed by delayed Version 6. Verifies Version 6 returns `STALE` and the database retains Version 7.
4. **Duplicate Replay (Idempotency)**: Replays identical Version 7 event. Verifies outcome is `DUPLICATE` with zero additional writes.
5. **Field Ownership Separation**: Transport Admin sets pickup point to `POINT-NORTH-GATE-01`. HR subsequently submits profile updates. Verifies pickup point remains intact and untouched.
6. **Safety Brake Circuit Breaker**: Uploads a truncated reconciliation snapshot (5 active in DB, 3 in snapshot = 40% missing vs 30% threshold). Verifies status is `BLOCKED_REQUIRES_APPROVAL` with **0 automatic deactivations**.
7. **Operator Approval**: Operator explicitly calls `POST /api/v1/reconciliations/{runId}/approve`. Verifies run executes and deactivations complete safely.
8. **Transactional Outbox Event Delivery**: Verifies outbox event status transitions from `PENDING` to `PUBLISHED`.
9. **Downstream Future Booking Cancellation**: Verifies future cab booking `BOOK-001` is marked `CANCELLED`.
10. **Downstream Seat Release**: Verifies vehicle seat assignment is cleared (`NULL`).
11. **Historical Past Booking Preservation**: Verifies past completed trip `BOOK-002` remains `CONFIRMED`.
12. **Consumer Idempotent Deduplication**: Verifies duplicate event receipt does not trigger redundant side effects.

---

## 5. Test Evidence

The test suite consists of 24 automated tests covering unit, integration, concurrency, failure-injection, and performance benchmarks:

```
[INFO] Results:
[INFO] Tests run: 24, Failures: 0, Errors: 0, Skipped: 0
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  24.761 s
```

### Test Breakdown
- **Unit Tests (13/13 PASS)**:
  - `CanonicalHasherTest`: Deterministic SHA-256 payload calculation and transport field exclusion (3 tests).
  - `EventValidationServiceTest`: Email RFC-5322, phone E.164, office codes, and partial batch success (5 tests).
  - `VersionManagementPolicyTest`: Monotonic ordering, stale rejection, version conflicts (5 tests).
- **Integration Tests (6/6 PASS)**:
  - `IdempotencyAndOrderingIntegrationTest`: Duplicate detection and out-of-order execution against database (2 tests).
  - `FieldOwnershipIntegrationTest`: HR updates preserving transport-owned pickup points (1 test).
  - `DeactivationSideEffectsIntegrationTest`: Future booking cancellation, seat release, past booking preservation (1 test).
  - `ReconciliationSafetyBrakeIntegrationTest`: Circuit breaker threshold triggering and operator approval (1 test).
  - `TenantIsolationIntegrationTest`: Cross-tenant data isolation and JWT context enforcement (1 test).
- **Concurrency Tests (2/2 PASS)**:
  - `ConcurrentEventIngestionTest`: Multi-threaded contention on identical employee records (2 tests).
- **Failure Recovery Tests (1/1 PASS)**:
  - `OutboxCrashRecoveryTest`: Publisher network interruption and expired lease recovery with zero data loss (1 test).
- **Performance Benchmarks (2/2 PASS)**:
  - `ReconciliationBenchmarkTest`: Full 200,000-row streaming reconciliation (1 test).
  - `HrEventLatencyBenchmarkTest`: Real-time HR event ingestion throughput and p50/p99 latency (1 test).

---

## 6. Performance & Benchmark Evidence

### 6.1 HR Event Ingestion Latency Benchmark (`HrEventLatencyBenchmarkTest`)
- **Dataset**: 500 measured synthetic events (following 50 warm-up requests)
- **Outcome**: 500 / 500 successful (0 failures, 100% success)
- **Throughput**: **1,048.00 events/second**
- **Average Latency**: **0.922 ms**
- **p50 Latency**: **0.725 ms**
- **p95 Latency**: **2.298 ms**
- **p99 Latency**: **3.099 ms**

### 6.2 Nightly 200,000-Row Reconciliation Benchmark (`ReconciliationBenchmarkTest`)
- **Dataset**: 200,000 synthetic employee records in a streaming CSV snapshot.
- **Execution Time**: **7.12 seconds** (Target threshold: < 60 seconds; ~8.4x faster than limit).
- **Throughput**: **28,082 rows/second**.
- **Heap Memory Delta**: **226 MB** (Bounded heap via 1,000-row batch chunking).
- **Host Hardware**: Windows 11 amd64, 22 CPU cores, 32 GB RAM.
- **Database Engine**: PostgreSQL 16 with indexed composite keys.

### Complexity Characteristics
- **Single Event**: $O(1)$ runtime, $O(1)$ memory.
- **Streaming Parser**: $O(N)$ runtime, $O(B)$ memory relative to file size ($B=1000$).
- **Canonical Hash**: $O(N)$ string hashing over bounded HR identity fields.
- **Staging & Diff**: $O(N \log N)$ database-side indexed hash/merge join. Avoids shipping entire employee dataset into application heap.

---

## 7. Key Architectural Decisions

### 1. PostgreSQL Transactional Outbox vs External Kafka
- **Decision**: Implemented a durable PostgreSQL transactional outbox polled via `FOR UPDATE SKIP LOCKED` with leased worker execution. An external Kafka/Redpanda broker was intentionally deferred.
- **Rationale**: In an HR roster synchronization engine, database consistency is paramount. Enqueuing outbox events in the same database transaction guarantees zero dual-write anomalies (where an event is emitted to a broker but database rollback occurs, or vice versa). PostgreSQL skip-locked polling easily delivers thousands of events/sec without the operational complexity and failure modes of a distributed Kafka cluster.

### 2. Physical Schema Field Ownership
- **Decision**: HR fields (`employees`) and Transport preferences (`employee_transport_profiles`) reside in separate physical tables.
- **Rationale**: Enforces field ownership at the database level. Even if an application bug passes transport fields to an HR update query, the SQL statement physically touches only the `employees` table, preventing commute route disruption.

### 3. Persistent Safety Brake Circuit Breaker
- **Decision**: Reconciliation runs, missing ratios, and operator approval states are persisted in `reconciliation_runs`.
- **Rationale**: An in-memory circuit breaker would lose its state if the server restarts during an anomalous reconciliation. Persisted state ensures the safety brake survives node failures and provides a tamper-resistant compliance audit trail.

---

## 8. Known Limitations & Disclosure

1. **Host Runtime Environment**:
   - The application compiler target is strictly configured to Java 21 bytecode (`<maven.compiler.release>21</maven.compiler.release>`). The local verification run was executed using the installed host OpenJDK 25 runtime running Java 21 release compatibility.
2. **Docker Runtime Verification**:
   - Multi-stage `Dockerfile` and `docker-compose.yml` configurations were authored and syntax-validated with `docker compose config`. Full containerized execution was marked `NOT VERIFIED` due to host Docker Desktop WSL2 engine startup delay.
3. **Staging Storage for Extreme Scale**:
   - The current JDBC batch staging handles 200,000 rows in 6.76 seconds. For enterprise datasets exceeding 10,000,000 rows, staging should transition from relational tables to external object storage (AWS S3 / GCS) queried via DuckDB or Presto.
