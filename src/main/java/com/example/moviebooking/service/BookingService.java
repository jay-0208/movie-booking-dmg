package com.example.moviebooking.service;

import com.example.moviebooking.entity.*;
import com.example.moviebooking.exception.BookingNotFoundException;
import com.example.moviebooking.exception.InvalidBookingStateException;
import com.example.moviebooking.exception.SeatUnavailableException;
import com.example.moviebooking.repository.BookingRepository;
import com.example.moviebooking.repository.ShowRepository;
import com.example.moviebooking.repository.ShowSeatRepository;
import com.example.moviebooking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Core booking flow. The seat-hold step is the part the assignment is really
 * testing: multiple users can hit holdSeats() for the same show+seat at the
 * same instant, and only one may win.
 *
 * How correctness is enforced here:
 *   1. findByShowIdAndSeatIdsForUpdate() takes a PESSIMISTIC_WRITE row lock
 *      (SELECT ... FOR UPDATE) on exactly the ShowSeat rows being requested.
 *      A second concurrent transaction requesting ANY of the same seats blocks
 *      at the query until the first transaction commits or rolls back - it
 *      cannot read a stale "AVAILABLE" and race ahead.
 *   2. The whole method is @Transactional, so the lock is held for the entire
 *      check-then-set sequence, not just the SELECT.
 *   3. @Version on ShowSeat gives a second, cheap line of defense (useful if
 *      you later relax the pessimistic lock for scale reasons).
 *
 * Alternative you could justify instead (document your choice in README):
 *   - Optimistic locking only (no PESSIMISTIC_WRITE), retry on
 *     OptimisticLockException. Better throughput under low contention, worse
 *     UX under high contention (popular show, seats open at midnight).
 *   - A Redis-based distributed lock per seat if you outgrow a single DB.
 */
@Service
@RequiredArgsConstructor
public class BookingService {

    private final ShowSeatRepository showSeatRepository;
    private final ShowRepository showRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final PricingService pricingService;
    private final NotificationService notificationService;

    @Value("${app.seat-hold.expiry-minutes}")
    private int holdExpiryMinutes;

    @Transactional
    public Booking holdSeats(Long userId, Long showId, List<Long> seatIds, String discountCode) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new IllegalArgumentException("Show not found: " + showId));

        // Lock the exact rows we're about to mutate. Order seatIds before locking
        // if you want to also avoid deadlocks between two multi-seat bookings that
        // request overlapping seats in different order.
        List<ShowSeat> showSeats = showSeatRepository.findByShowIdAndSeatIdsForUpdate(showId, seatIds);

        if (showSeats.size() != seatIds.size()) {
            throw new SeatUnavailableException("One or more requested seats do not exist for this show");
        }

        for (ShowSeat ss : showSeats) {
            if (ss.getStatus() != SeatStatus.AVAILABLE) {
                throw new SeatUnavailableException(
                        "Seat " + ss.getSeat().getRowLabel() + ss.getSeat().getSeatNumber() + " is no longer available");
            }
        }

        BigDecimal totalAmount = pricingService.calculateTotal(show, showSeats, discountCode);

        Booking booking = Booking.builder()
                .user(user)
                .show(show)
                .status(BookingStatus.PENDING_PAYMENT)
                .totalAmount(totalAmount)
                .createdAt(LocalDateTime.now())
                .holdExpiresAt(LocalDateTime.now().plusMinutes(holdExpiryMinutes))
                .build();
        booking = bookingRepository.save(booking);

        LocalDateTime holdExpiresAt = booking.getHoldExpiresAt();
        for (ShowSeat ss : showSeats) {
            ss.setStatus(SeatStatus.HELD);
            ss.setHoldExpiresAt(holdExpiresAt);
            ss.setBooking(booking);
        }
        showSeatRepository.saveAll(showSeats);

        return booking;
    }

    // Called after "payment" succeeds (see PaymentService). Flips HELD -> BOOKED
    // and clears the hold expiry so the sweep job leaves it alone.
    @Transactional
    public void confirmBooking(Booking booking, List<ShowSeat> showSeats) {
        booking.setStatus(BookingStatus.CONFIRMED);
        bookingRepository.save(booking);

        for (ShowSeat ss : showSeats) {
            ss.setStatus(SeatStatus.BOOKED);
            ss.setHoldExpiresAt(null);
        }
        showSeatRepository.saveAll(showSeats);

        // Fire-and-forget - must NOT block or fail the booking transaction.
        notificationService.sendBookingConfirmation(booking);
    }

    // Convenience overload for the controller/PaymentService: load booking + its
    // seats by id, validate state, then delegate to the core confirm method above.
    // Kept separate from confirmBooking(Booking, List<ShowSeat>) so PaymentService
    // (step 3) can still pass in-hand objects when it already has them loaded.
    @Transactional
    public void confirmBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found: " + bookingId));

        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new InvalidBookingStateException(
                    "Booking " + bookingId + " is not awaiting payment (current status: " + booking.getStatus() + ")");
        }

        List<ShowSeat> showSeats = showSeatRepository.findByBookingId(bookingId);
        confirmBooking(booking, showSeats);
    }

    @Transactional(readOnly = true)
    public Booking getBookingForUser(Long bookingId, Long userId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found: " + bookingId));

        // Returning 404 (via the same exception) rather than 403 for a booking that
        // exists but belongs to someone else - avoids confirming booking-id existence
        // to a user who shouldn't see it either way.
        if (!booking.getUser().getId().equals(userId)) {
            throw new BookingNotFoundException("Booking not found: " + bookingId);
        }
        return booking;
    }

    @Transactional(readOnly = true)
    public List<Booking> getBookingsForUser(Long userId) {
        return bookingRepository.findByUserId(userId);
    }
}
