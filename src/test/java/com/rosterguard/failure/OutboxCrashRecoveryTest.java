package com.rosterguard.failure;

import com.rosterguard.api.dto.HrEventRequest;
import com.rosterguard.application.ingestion.EmployeeIngestionService;
import com.rosterguard.application.outbox.EventPublisher;
import com.rosterguard.application.outbox.OutboxPollingWorker;
import com.rosterguard.domain.booking.BookingStatus;
import com.rosterguard.domain.event.HrEventType;
import com.rosterguard.infrastructure.persistence.CabBookingRepository;
import com.rosterguard.infrastructure.persistence.OutboxRepository;
import com.rosterguard.integration.BaseIntegrationTest;
import com.rosterguard.observability.RosterMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class OutboxCrashRecoveryTest extends BaseIntegrationTest {

    @Autowired
    private EmployeeIngestionService ingestionService;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private CabBookingRepository cabBookingRepository;

    @Autowired
    private RosterMetrics rosterMetrics;

    private static final String TENANT_ID = "tenant-moveinsync";
    private static final String EMP_ID = "EMP-CRASH-001";

    @BeforeEach
    void setup() {
        cleanDatabase();
    }

    @Test
    @DisplayName("Mandatory Failure Scenario: Process crash between DB commit and event publish recovers pending events without data loss")
    void crashBetweenDatabaseWriteAndPublish_recoversPendingEvents() {
        // Step 1: Create active employee and future cab booking
        Instant now = Instant.now();
        HrEventRequest createReq = new HrEventRequest(
                "evt-init", "WORKDAY", EMP_ID, 1L, HrEventType.CREATE,
                now, "Crash Tester", "crashtest@moveinsync.com", "+919876543210", "MGR-01", "BLR-01"
        );
        ingestionService.processSingleEvent(TENANT_ID, createReq);

        cabBookingRepository.insertBooking("BOOK-CRASH-FUT", TENANT_ID, EMP_ID,
                now.plus(Duration.ofHours(48)), BookingStatus.CONFIRMED, "SEAT-CRASH-1");

        // Step 2: Ingest employee deactivation.
        // Single PostgreSQL ACID transaction commits:
        //  - Employee active = false
        //  - Idempotency receipt saved
        //  - Outbox event written with status = 'PENDING'
        HrEventRequest deactReq = new HrEventRequest(
                "evt-deact-crash", "WORKDAY", EMP_ID, 2L, HrEventType.DEACTIVATE,
                now, null, null, null, null, null
        );
        ingestionService.processSingleEvent(TENANT_ID, deactReq);

        // Verify that outbox event is in database in PENDING status
        int pendingBeforeWorker = outboxRepository.countPendingEvents();
        assertTrue(pendingBeforeWorker >= 1, "Outbox event must be durably stored in PostgreSQL before publication");

        // Step 3: Simulate failure / crash during first publisher dispatch attempt!
        AtomicBoolean simulatedCrashTriggered = new AtomicBoolean(false);
        EventPublisher crashingPublisher = event -> {
            simulatedCrashTriggered.set(true);
            throw new RuntimeException("SIMULATED BROKER DISPATCH NETWORK PARTITION / CRASH!");
        };

        OutboxPollingWorker crashingWorker = new OutboxPollingWorker(
                outboxRepository, crashingPublisher, rosterMetrics, 50, 5
        );

        // Crashing worker attempts dispatch and fails
        crashingWorker.processPendingOutboxBatch();
        assertTrue(simulatedCrashTriggered.get(), "Simulated crash should have been triggered");

        // Booking is still CONFIRMED because publisher crashed before delivery
        List<CabBookingRepository.CabBooking> bookingsDuringCrash = cabBookingRepository.findBookingsByEmployee(TENANT_ID, EMP_ID);
        assertEquals(BookingStatus.CONFIRMED, bookingsDuringCrash.get(0).bookingStatus());

        // Step 4: System restarts / recovered worker comes online!
        // Reset next_attempt_at to now for immediate retry recovery
        jdbcTemplate.execute("UPDATE outbox_events SET next_attempt_at = NOW()");

        AtomicBoolean recoveredDeliveryTriggered = new AtomicBoolean(false);
        EventPublisher recoveredPublisher = event -> {
            recoveredDeliveryTriggered.set(true);
            cabBookingRepository.cancelFutureBookingsAndFreeSeats(event.getTenantId(), event.getAggregateId(), Instant.now());
        };

        OutboxPollingWorker recoveredWorker = new OutboxPollingWorker(
                outboxRepository, recoveredPublisher, rosterMetrics, 50, 5
        );

        // Step 5: Recovered worker polls pending events
        int recoveredCount = recoveredWorker.processPendingOutboxBatch();
        assertTrue(recoveredCount >= 1, "Recovered worker should discover and process pending outbox events");
        assertTrue(recoveredDeliveryTriggered.get(), "Pending event was successfully published after restart");

        // Step 6: Verify downstream side effect is now complete: booking cancelled and seat freed!
        List<CabBookingRepository.CabBooking> finalBookings = cabBookingRepository.findBookingsByEmployee(TENANT_ID, EMP_ID);
        assertEquals(BookingStatus.CANCELLED, finalBookings.get(0).bookingStatus());
        assertNull(finalBookings.get(0).seatId(), "Seat must be released");

        // Outbox event is marked PUBLISHED
        assertEquals(0, outboxRepository.countPendingEvents(), "All outbox events should now be published");
    }
}
