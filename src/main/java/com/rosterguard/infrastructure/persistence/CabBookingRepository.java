package com.rosterguard.infrastructure.persistence;

import com.rosterguard.domain.booking.BookingStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class CabBookingRepository {

    private final JdbcTemplate jdbcTemplate;

    public CabBookingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record CabBooking(String bookingId, String tenantId, String employeeId,
                             Instant pickupTime, BookingStatus bookingStatus, String seatId, Instant updatedAt) {}

    public void insertBooking(String bookingId, String tenantId, String employeeId,
                              Instant pickupTime, BookingStatus status, String seatId) {
        String sql = """
            INSERT INTO cab_bookings (booking_id, tenant_id, employee_id, pickup_time, booking_status, seat_id, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
        jdbcTemplate.update(sql, bookingId, tenantId, employeeId, Timestamp.from(pickupTime), status.name(), seatId, Timestamp.from(Instant.now()));
    }

    public List<CabBooking> findFutureActiveBookings(String tenantId, String employeeId, Instant asOf) {
        String sql = """
            SELECT booking_id, tenant_id, employee_id, pickup_time, booking_status, seat_id, updated_at
            FROM cab_bookings
            WHERE tenant_id = ? AND employee_id = ? AND pickup_time > ? AND booking_status = 'CONFIRMED'
        """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new CabBooking(
                rs.getString("booking_id"),
                rs.getString("tenant_id"),
                rs.getString("employee_id"),
                rs.getTimestamp("pickup_time").toInstant(),
                BookingStatus.valueOf(rs.getString("booking_status")),
                rs.getString("seat_id"),
                rs.getTimestamp("updated_at").toInstant()
        ), tenantId, employeeId, Timestamp.from(asOf));
    }

    public int cancelFutureBookingsAndFreeSeats(String tenantId, String employeeId, Instant asOf) {
        String sql = """
            UPDATE cab_bookings
            SET booking_status = 'CANCELLED', seat_id = NULL, updated_at = ?
            WHERE tenant_id = ? AND employee_id = ? AND pickup_time > ? AND booking_status = 'CONFIRMED'
        """;
        return jdbcTemplate.update(sql, Timestamp.from(Instant.now()), tenantId, employeeId, Timestamp.from(asOf));
    }

    public List<CabBooking> findBookingsByEmployee(String tenantId, String employeeId) {
        String sql = "SELECT * FROM cab_bookings WHERE tenant_id = ? AND employee_id = ? ORDER BY pickup_time ASC";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new CabBooking(
                rs.getString("booking_id"),
                rs.getString("tenant_id"),
                rs.getString("employee_id"),
                rs.getTimestamp("pickup_time").toInstant(),
                BookingStatus.valueOf(rs.getString("booking_status")),
                rs.getString("seat_id"),
                rs.getTimestamp("updated_at").toInstant()
        ), tenantId, employeeId);
    }
}
