package com.rosterguard.benchmark;

import com.rosterguard.api.dto.HrEventRecordOutcome;
import com.rosterguard.api.dto.HrEventRequest;
import com.rosterguard.application.ingestion.EmployeeIngestionService;
import com.rosterguard.domain.event.HrEventType;
import com.rosterguard.domain.event.ProcessingOutcome;
import com.rosterguard.integration.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HrEventLatencyBenchmarkTest extends BaseIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(HrEventLatencyBenchmarkTest.class);
    private static final String TENANT_ID = "tenant-moveinsync";

    @Autowired
    private EmployeeIngestionService ingestionService;

    @org.junit.jupiter.api.BeforeEach
    void setup() {
        cleanDatabase();
    }

    @Test
    @DisplayName("Performance Benchmark: Measure p50, p95, p99 latency and throughput for HR event ingestion")
    void measureHrEventProcessingLatencyAndThroughput() {
        int warmupCount = 50;
        int measuredCount = 500;

        // 1. Warm-up Phase (JIT compilation & connection pool priming)
        log.info("Starting warm-up phase with {} requests...", warmupCount);
        for (int i = 1; i <= warmupCount; i++) {
            HrEventRequest warmupReq = new HrEventRequest(
                    "warmup-" + i, "WORKDAY", "EMP-WARMUP-" + i, 1L, HrEventType.CREATE,
                    Instant.now(), "Warmup User " + i, "warmup" + i + "@moveinsync.com",
                    "+919876543210", "MGR-01", "BLR-01"
            );
            ingestionService.processSingleEvent(TENANT_ID, warmupReq);
        }

        // 2. Timed Measurement Phase
        log.info("Starting timed measurement of {} unique HR events...", measuredCount);
        List<Double> latenciesMs = new ArrayList<>(measuredCount);
        int successCount = 0;
        int failureCount = 0;

        long overallStartNanos = System.nanoTime();

        for (int i = 1; i <= measuredCount; i++) {
            String empId = "EMP-LATENCY-" + String.format("%05d", i);
            HrEventRequest req = new HrEventRequest(
                    "evt-" + empId, "WORKDAY", empId, 1L, HrEventType.CREATE,
                    Instant.now(), "Latency Employee " + i, "latency" + i + "@moveinsync.com",
                    "+919876543210", "MGR-01", "BLR-01"
            );

            long startNanos = System.nanoTime();
            HrEventRecordOutcome outcome = ingestionService.processSingleEvent(TENANT_ID, req);
            long endNanos = System.nanoTime();

            double latencyMs = (endNanos - startNanos) / 1_000_000.0;
            latenciesMs.add(latencyMs);

            if (outcome.getOutcome() == ProcessingOutcome.APPLIED) {
                successCount++;
            } else {
                failureCount++;
            }
        }

        long overallEndNanos = System.nanoTime();
        double overallDurationSec = (overallEndNanos - overallStartNanos) / 1_000_000_000.0;
        double throughput = measuredCount / overallDurationSec;

        // 3. Percentile Calculations (Nearest-Rank method)
        Collections.sort(latenciesMs);
        double p50 = getPercentile(latenciesMs, 50);
        double p95 = getPercentile(latenciesMs, 95);
        double p99 = getPercentile(latenciesMs, 99);
        double avgLatency = latenciesMs.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        // Hardware details
        Runtime runtime = Runtime.getRuntime();
        String os = System.getProperty("os.name") + " " + System.getProperty("os.arch");
        String javaVersion = System.getProperty("java.runtime.version");

        String banner = String.format("\n" +
                "===============================================================\n" +
                "ROSTERGUARD HR EVENT PROCESSING LATENCY BENCHMARK RESULTS:\n" +
                "Total Events Measured: %d\n" +
                "Successful Events:     %d\n" +
                "Failed / Rejected:     %d\n" +
                "Overall Duration:      %.3f seconds\n" +
                "Throughput:            %.2f events/second\n" +
                "Average Latency:       %.3f ms\n" +
                "p50 (Median) Latency:  %.3f ms\n" +
                "p95 Latency:           %.3f ms\n" +
                "p99 Latency:           %.3f ms\n" +
                "OS / Arch:             %s\n" +
                "Java Version:          %s\n" +
                "Available Cores:       %d\n" +
                "===============================================================",
                measuredCount, successCount, failureCount, overallDurationSec, throughput,
                avgLatency, p50, p95, p99, os, javaVersion, runtime.availableProcessors());

        log.info("{}", banner);

        // Assertions
        assertEquals(measuredCount, successCount, "All valid synthetic events should be APPLIED");
        assertEquals(0, failureCount, "Zero events should fail validation");
        assertTrue(p99 < 150.0, "p99 latency should be well under 150ms for local service-level processing");
    }

    private double getPercentile(List<Double> sortedLatencies, double percentile) {
        int index = (int) Math.ceil((percentile / 100.0) * sortedLatencies.size()) - 1;
        index = Math.max(0, Math.min(index, sortedLatencies.size() - 1));
        return sortedLatencies.get(index);
    }
}
