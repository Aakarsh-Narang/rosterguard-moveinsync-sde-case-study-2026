package com.rosterguard.concurrency;

import com.rosterguard.api.dto.HrEventRecordOutcome;
import com.rosterguard.api.dto.HrEventRequest;
import com.rosterguard.application.ingestion.EmployeeIngestionService;
import com.rosterguard.domain.employee.Employee;
import com.rosterguard.domain.event.HrEventType;
import com.rosterguard.domain.event.ProcessingOutcome;
import com.rosterguard.infrastructure.persistence.EmployeeRepository;
import com.rosterguard.integration.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrentEventIngestionTest extends BaseIntegrationTest {

    @Autowired
    private EmployeeIngestionService ingestionService;

    @Autowired
    private EmployeeRepository employeeRepository;

    private static final String TENANT_ID = "tenant-moveinsync";

    @BeforeEach
    void setup() {
        cleanDatabase();
    }

    @Test
    @DisplayName("Concurrency Test: 10 concurrent identical requests produce exactly one APPLIED, others DUPLICATE")
    void concurrentIdenticalRequests_produceOneApplication() throws InterruptedException {
        String empId = "EMP-CONCUR-IDEM";
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        List<HrEventRecordOutcome> outcomes = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    HrEventRequest req = new HrEventRequest(
                            "evt-dup-" + Thread.currentThread().getId(), "WORKDAY", empId, 1L, HrEventType.CREATE,
                            Instant.now(), "Concurrent Worker", "worker@moveinsync.com", "+919876543210", "MGR-01", "BLR-01"
                    );
                    HrEventRecordOutcome outcome = ingestionService.processSingleEvent(TENANT_ID, req);
                    outcomes.add(outcome);
                } catch (Exception e) {
                    fail("Unexpected exception during concurrency execution: " + e.getMessage());
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Fire all threads simultaneously
        finishLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(threadCount, outcomes.size());

        long appliedCount = outcomes.stream().filter(o -> o.getOutcome() == ProcessingOutcome.APPLIED).count();
        long duplicateCount = outcomes.stream().filter(o -> o.getOutcome() == ProcessingOutcome.DUPLICATE).count();

        assertEquals(1, appliedCount, "Exactly one concurrent thread should win and be APPLIED");
        assertEquals(threadCount - 1, duplicateCount, "All other identical concurrent requests must be DUPLICATE no-ops");
    }

    @Test
    @DisplayName("Concurrency Test: Concurrent Version 6 and Version 7 updates never regress state")
    void concurrentOutOfOrderRequests_neverRegressVersion() throws InterruptedException {
        String empId = "EMP-CONCUR-ORDER";
        int iterations = 5;

        for (int round = 0; round < iterations; round++) {
            cleanDatabase();

            // Initial base version 5
            HrEventRequest v5 = new HrEventRequest(
                    "evt-v5", "WORKDAY", empId, 5L, HrEventType.CREATE,
                    Instant.now(), "Base Worker", "base@moveinsync.com", "+919876543210", "MGR-01", "BLR-01"
            );
            ingestionService.processSingleEvent(TENANT_ID, v5);

            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch finishLatch = new CountDownLatch(2);

            // Thread 1 sends version 6 (BLR-01)
            executor.submit(() -> {
                try {
                    startLatch.await();
                    HrEventRequest v6 = new HrEventRequest(
                            "evt-v6", "WORKDAY", empId, 6L, HrEventType.UPDATE,
                            Instant.now(), "Worker Shift 6", "shift6@moveinsync.com", "+919876543210", "MGR-01", "BLR-01"
                    );
                    ingestionService.processSingleEvent(TENANT_ID, v6);
                } catch (Exception ignored) {
                } finally {
                    finishLatch.countDown();
                }
            });

            // Thread 2 sends version 7 (HYD-01 transfer)
            executor.submit(() -> {
                try {
                    startLatch.await();
                    HrEventRequest v7 = new HrEventRequest(
                            "evt-v7", "WORKDAY", empId, 7L, HrEventType.UPDATE,
                            Instant.now(), "Worker Transfer 7", "trans7@moveinsync.com", "+919876543210", "MGR-01", "HYD-01"
                    );
                    ingestionService.processSingleEvent(TENANT_ID, v7);
                } catch (Exception ignored) {
                } finally {
                    finishLatch.countDown();
                }
            });

            startLatch.countDown();
            finishLatch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            // The final state MUST be Version 7 and office HYD-01, NEVER regressing to Version 6!
            Optional<Employee> finalEmployee = employeeRepository.findById(TENANT_ID, empId);
            assertTrue(finalEmployee.isPresent());
            assertEquals(7L, finalEmployee.get().getSourceVersion(),
                    "Under concurrent execution, final version must be 7, never regressing to 6");
            assertEquals("HYD-01", finalEmployee.get().getOfficeCode(),
                    "Under concurrent execution, office must be HYD-01 from version 7");
        }
    }
}
