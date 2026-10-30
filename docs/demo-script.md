# RosterGuard — 3–5 Minute Live Demonstration & Presentation Script
**MoveInSync Case Study 5: HR Roster Sync (Idempotent, Out-of-Order, Self-Healing)**  
**Target:** Advanced SDE Placement Assessment  
**Author:** Aakarsh-Narang (<184413376+Aakarsh-Narang@users.noreply.github.com>)

---

## Overview

This guide provides a structured, timed 3–5 minute presentation script accompanied by exact console commands and expected screen outputs. It also contains instructions for capturing a high-quality screen recording on Windows.

---

## Recording Setup Instructions (For Developer)

1. **Screen Recording Tools**:
   - **Option A (Native Windows Xbox Game Bar)**: Press `Win + Alt + R` to start/stop screen recording, or `Win + G` to open the recording panel.
   - **Option B (OBS Studio / ShareX)**: Recommended for full-desktop capture at 1080p, 60fps.
2. **Terminal Preparation**:
   - Open Windows Terminal (PowerShell or Command Prompt).
   - Set terminal font size to 14pt+ for high readability.
   - Ensure the Spring Boot backend is running in one terminal window (`.\mvnw.bat spring-boot:run`).
   - Have a second terminal window ready at the project root to run:
     ```cmd
     .\scripts\demo_api.bat
     ```

---

## Timed Presentation Script (3–5 Minutes)

### Segment 1: Opening & Problem Statement (0:00 – 0:45)
- **Visual**: Show the project `README.md` or architecture diagram in `DESIGN.md`.
- **Spoken Script**:
  > *"Hello, I'm Aakarsh Narang. Today I am presenting **RosterGuard**, an idempotent, out-of-order, self-healing HR synchronization engine designed for MoveInSync's Case Study 5.  
  > In an enterprise commute platform, synchronizing employee rosters between corporate HR systems like Workday and the transport routing fleet is a classic distributed systems challenge. Network latency causes updates to arrive out of order; retries cause duplicates; partial network drops truncate nightly CSV files; and HR updates risk accidentally wiping out transport-specific commuter preferences.  
  > RosterGuard solves every one of these failure modes with strict ACID database guarantees, monotonic version tracking, physical field ownership, a transactional outbox with crash recovery, and a safety brake circuit breaker. Let's look at the live demonstration."*

---

### Segment 2: System Health & Idempotency Demonstration (0:45 – 1:30)
- **Visual**: Switch to terminal and run `.\scripts\demo_api.bat`. Observe the automated health check and Demo Steps 1, 2, 3, and 4.
- **Spoken Script**:
  > *"Here I'm launching our automated demonstration script against our running Spring Boot application.  
  > First, the script verifies system health and readiness at `/actuator/health`. Then it authenticates using tenant-scoped JWT credentials and creates employee `EMP-DEMO-001` with version 1.  
  > Notice what happens next: an out-of-order event arrives. Version 7—a promotion to Engineering Lead—is applied first. Moments later, a delayed network packet delivers Version 6. Instead of rolling back the employee's state, RosterGuard's `VersionManagementPolicy` detects the stale version, records an audit receipt, and discards the update. The database retains Version 7.  
  > In step 4, we simulate an identical event retry by resubmitting Version 7. The composite key and SHA-256 payload hash immediately identify it as a `DUPLICATE`. The caller receives HTTP 200, and zero redundant database writes occur."*

---

### Segment 3: Field Ownership Separation (1:30 – 2:15)
- **Visual**: Observe Demo Step 5 in the terminal output.
- **Spoken Script**:
  > *"Next is field ownership. Commuter pickup points and special assistance requirements are owned exclusively by MoveInSync Transport Administrators, while HR owns identity and office location.  
  > In step 5, the transport administrator sets this employee's pickup point to `POINT-NORTH-GATE-01`. Immediately afterward, HR submits a subsequent employee profile update.  
  > Because RosterGuard enforces physical schema separation—storing transport preferences in `employee_transport_profiles` rather than a shared wide table—the HR update physically touches only the `employees` table. The pickup point remains completely untouched, preventing commute disruption."*

---

### Segment 4: Nightly Reconciliation & Safety Brake Circuit Breaker (2:15 – 3:15)
- **Visual**: Observe Demo Step 6 in the terminal output showing `BLOCKED_REQUIRES_APPROVAL`.
- **Spoken Script**:
  > *"Now let's examine nightly reconciliation. In production, MoveInSync reconciles up to 200,000 employees every night. But what happens if an upstream network glitch cuts the snapshot short?  
  > In our test, we have 5 active employees in the database, but the uploaded snapshot only contains 3—a 40% missing ratio. If diffed blindly, the system would immediately deactivate the 2 missing employees and strand them without cabs.  
  > Instead, RosterGuard's configurable Safety Brake triggers. Because the missing ratio exceeds our 30% threshold, the run is automatically placed into `BLOCKED_REQUIRES_APPROVAL`. Crucially, exactly zero employees are auto-deactivated.  
  > The system alerts the operations team. In step 6B, an authorized operator reviews the run and calls the `/approve` endpoint. Only then does the engine proceed to safely apply the deactivations."*

---

### Segment 5: Transactional Outbox, Downstream Booking & Deduplication (3:15 – 4:00)
- **Visual**: Observe Demo Steps 7, 8, 9, 10, and 11 in the terminal output.
- **Spoken Script**:
  > *"When an employee is deactivated, downstream systems must react. To prevent dual-write bugs, RosterGuard writes both the employee state and an outbox event in a single database transaction.  
  > An asynchronous background worker polls the outbox using `FOR UPDATE SKIP LOCKED` and dispatches the event. The downstream `DeactivationConsumer` immediately cancels the employee's future cab bookings and releases their assigned vehicle seat back into the fleet.  
  > Historical past bookings are strictly preserved for billing and compliance. Furthermore, the consumer writes to `consumed_events`, guaranteeing that duplicate event delivery never causes repeated side effects."*

---

### Segment 6: Performance Benchmark & Architectural Trade-offs (4:00 – 4:45)
- **Visual**: Highlight the benchmark metrics in `README.md` or test log.
- **Spoken Script**:
  > *"Regarding performance, our 200,000-row reconciliation benchmark processes, validates, stages, and diffs a full 200,000-row CSV snapshot in just **7.12 seconds**—over 8 times faster than the 60-second assessment threshold—at roughly **28,082 rows per second** with a modest 226 MB heap delta, thanks to OpenCSV streaming and JDBC batching.  
  > Furthermore, our real-time HR event ingestion benchmark achieved over **1,048 events per second** with a median latency of **0.725 milliseconds** and a 99th-percentile latency of **3.099 milliseconds**.  
  > Architecturally, we deliberately implemented a PostgreSQL transactional outbox instead of introducing an external Kafka broker. This guarantees atomic consistency with zero dual-write anomalies while keeping deployment lightweight and operationally bulletproof.  
  > In conclusion, RosterGuard meets all 10 core requirements with 24 passing automated tests and a 100% verified live demonstration. Thank you."*

---

## Demonstration Checklist for Submission Video

- [ ] Spring Boot application launched and healthy.
- [ ] Terminal recording at 1080p resolution.
- [ ] Automated script runs from start to finish without errors (`EXIT CODE: 0`).
- [ ] All 12 individual checks output `[PASS]`.
- [ ] Clear narration covering ordering, idempotency, field ownership, safety brake, and outbox delivery.
