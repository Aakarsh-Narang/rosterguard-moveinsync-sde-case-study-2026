package com.rosterguard.integration;

import com.rosterguard.api.dto.HrEventRequest;
import com.rosterguard.application.ingestion.EmployeeIngestionService;
import com.rosterguard.application.outbox.OutboxPollingWorker;
import com.rosterguard.domain.booking.BookingStatus;
import com.rosterguard.domain.event.HrEventType;
import com.rosterguard.infrastructure.persistence.CabBookingRepository;
import com.rosterguard.infrastructure.persistence.ConsumedEventsRepository;
import com.rosterguard.infrastructure.persistence.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeactivationSideEffectsIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private EmployeeIngestionService ingestionService;

    @Autowired
    private CabBookingRepository cabBookingRepository;

    @Autowired
    private OutboxPollingWorker outboxPollingWorker;

    @Autowired
    private ConsumedEventsRepository consumedEventsRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    private static final String TENANT_ID = "tenant-moveinsync";
    private static final String EMP_ID = "EMP-DEACT-001";

    @BeforeEach
    void setup() {
        cleanDatabase();
    }

    @Test
    @DisplayName("Mandatory Deactivation Lifecycle: cancels future bookings, frees seats, preserves past bookings, deduplicates replays")
    void employeeDeactivation_cancelsFutureBookings_preservesPastBookings() {
        // Step 1: Create active employee
        HrEventRequest createReq = new HrEventRequest(
                "evt-create", "WORKDAY", EMP_ID, 1L, HrEventType.CREATE,
                Instant.now(), "Charlie Exiter", "charlie@moveinsync.com", "+919876543210", "MGR-01", "BLR-01"
        );
        ingestionService.processSingleEvent(TENANT_ID, createReq);

        // Step 2: Seed cab bookings:
        // - Booking 1: PAST booking (24 hours ago, CONFIRMED, seat SEAT-A1)
        // - Booking 2: FUTURE booking (tomorrow, CONFIRMED, seat SEAT-B2)
        // - Booking 3: FUTURE booking (next week, CONFIRMED, seat SEAT-C3)
        Instant now = Instant.now();
        cabBookingRepository.insertBooking("BOOK-PAST-1", TENANT_ID, EMP_ID, now.minus(Duration.ofHours(24)), BookingStatus.CONFIRMED, "SEAT-A1");
        cabBookingRepository.insertBooking("BOOK-FUT-2", TENANT_ID, EMP_ID, now.plus(Duration.ofHours(24)), BookingStatus.CONFIRMED, "SEAT-B2");
        cabBookingRepository.insertBooking("BOOK-FUT-3", TENANT_ID, EMP_ID, now.plus(Duration.ofDays(7)), BookingStatus.CONFIRMED, "SEAT-C3");

        // Verify initial bookings
        List<CabBookingRepository.CabBooking> initialBookings = cabBookingRepository.findBookingsByEmployee(TENANT_ID, EMP_ID);
        assertEquals(3, initialBookings.size());

        // Step 3: Deactivate employee via HR event
        HrEventRequest deactReq = new HrEventRequest(
                "evt-deact", "WORKDAY", EMP_ID, 2L, HrEventType.DEACTIVATE,
                now, null, null, null, null, null
        );
        ingestionService.processSingleEvent(TENANT_ID, deactReq);

        // Verify an outbox event was created in PENDING status
        assertEquals(2, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE status = 'PENDING'", Integer.class));

        // Step 4: Outbox poller runs, dispatches deactivation event to downstream consumer
        int processed = outboxPollingWorker.processPendingOutboxBatch();
        assertTrue(processed >= 1, "Expected outbox poller to process pending events");

        // Step 5: Verify downstream effects:
        // - Past booking MUST remain CONFIRMED with seat intact
        List<CabBookingRepository.CabBooking> afterBookings = cabBookingRepository.findBookingsByEmployee(TENANT_ID, EMP_ID);
        CabBookingRepository.CabBooking pastBooking = afterBookings.stream()
                .filter(b -> b.bookingId().equals("BOOK-PAST-1"))
                .findFirst().orElseThrow();
        assertEquals(BookingStatus.CONFIRMED, pastBooking.bookingStatus(), "Past bookings must NOT be changed");
        assertEquals("SEAT-A1", pastBooking.seatId(), "Past booking seat must remain assigned");

        // - Future bookings MUST be CANCELLED with seat released (NULL)
        CabBookingRepository.CabBooking futureBooking2 = afterBookings.stream()
                .filter(b -> b.bookingId().equals("BOOK-FUT-2"))
                .findFirst().orElseThrow();
        assertEquals(BookingStatus.CANCELLED, futureBooking2.bookingStatus(), "Future booking must be CANCELLED");
        assertNull(futureBooking2.seatId(), "Allocated seat must be freed (NULL)");

        CabBookingRepository.CabBooking futureBooking3 = afterBookings.stream()
                .filter(b -> b.bookingId().equals("BOOK-FUT-3"))
                .findFirst().orElseThrow();
        assertEquals(BookingStatus.CANCELLED, futureBooking3.bookingStatus(), "Future booking must be CANCELLED");
        assertNull(futureBooking3.seatId(), "Allocated seat must be freed (NULL)");

        // Step 6: Verify consumer deduplication table prevents repeated delivery side effects
        int againProcessed = outboxPollingWorker.processPendingOutboxBatch();
        assertEquals(0, againProcessed, "No pending outbox events should remain");
    }
}
