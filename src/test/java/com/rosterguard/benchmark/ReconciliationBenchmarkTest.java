package com.rosterguard.benchmark;

import com.rosterguard.api.dto.ReconciliationRunResponse;
import com.rosterguard.application.reconciliation.ReconciliationService;
import com.rosterguard.domain.reconciliation.ReconciliationStatus;
import com.rosterguard.integration.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReconciliationBenchmarkTest extends BaseIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationBenchmarkTest.class);

    @Autowired
    private ReconciliationService reconciliationService;

    private static final String TENANT_ID = "tenant-moveinsync";

    @Test
    @DisplayName("Performance Benchmark: Streaming 200,000 CSV records with bounded memory and measuring throughput")
    void benchmark200kEmployeesReconciliation() throws Exception {
        int targetRows = 200_000;
        log.info("Generating temporary synthetic CSV file with {} records...", targetRows);

        File tempCsv = File.createTempFile("recon-200k-", ".csv");
        tempCsv.deleteOnExit();

        long genStart = System.currentTimeMillis();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempCsv, StandardCharsets.UTF_8), 64 * 1024)) {
            writer.write("employee_id,name,email,phone,manager_id,office_code,source_version,active\n");
            for (int i = 1; i <= targetRows; i++) {
                writer.write(String.format("EMP-BENCH-%06d,Employee Name %d,emp%d@moveinsync.com,+919876543210,MGR-%02d,BLR-01,1,true\n",
                        i, i, i, (i % 20) + 1));
            }
        }
        long genDuration = System.currentTimeMillis() - genStart;
        log.info("Generated {} rows in {} ms. File size: {} MB", targetRows, genDuration, tempCsv.length() / (1024 * 1024));

        // Measure initial memory
        System.gc();
        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();

        long startNanos = System.nanoTime();
        ReconciliationRunResponse response;

        try (InputStream inputStream = new BufferedInputStream(new FileInputStream(tempCsv), 64 * 1024)) {
            response = reconciliationService.processNightlySnapshot(TENANT_ID, inputStream, Instant.now());
        }

        long endNanos = System.nanoTime();
        long durationMs = (endNanos - startNanos) / 1_000_000;
        double durationSeconds = durationMs / 1000.0;

        System.gc();
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryDeltaMB = (finalMemory - initialMemory) / (1024 * 1024);

        double throughputRowsPerSec = durationSeconds > 0 ? targetRows / durationSeconds : targetRows;

        log.info("===============================================================");
        log.info("ROSTERGUARD 200,000 ROW RECONCILIATION BENCHMARK RESULTS:");
        log.info("Total Rows Processed: {}", response.getTotalRows());
        log.info("Valid Rows Staged: {}", response.getValidRows());
        log.info("Invalid Rows: {}", response.getInvalidRows());
        log.info("Status: {}", response.getStatus());
        log.info("Execution Time: {} ms ({} seconds)", durationMs, String.format("%.2f", durationSeconds));
        log.info("Throughput: {} rows/second", String.format("%.1f", throughputRowsPerSec));
        log.info("Heap Memory Delta: {} MB", memoryDeltaMB);
        log.info("JVM Max Memory: {} MB", runtime.maxMemory() / (1024 * 1024));
        log.info("===============================================================");

        assertEquals(targetRows, response.getTotalRows());
        assertEquals(targetRows, response.getValidRows());
        assertEquals(ReconciliationStatus.COMPLETED, response.getStatus());
        assertTrue(durationSeconds < 60.0, "Case study requires 200,000 rows to be processed in under 60 seconds");
    }
}
