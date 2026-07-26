package com.example.moviebooking.service;

import com.example.moviebooking.entity.Booking;
import com.example.moviebooking.entity.BookingStatus;
import com.example.moviebooking.entity.Payment;
import com.example.moviebooking.entity.PaymentStatus;
import com.example.moviebooking.exception.InvalidBookingStateException;
import com.example.moviebooking.exception.PaymentFailedException;
import com.example.moviebooking.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Simulated payment provider - no real PSP integration (out of scope per the
 * assignment). Exists as its own service, separate from BookingService, so
 * the "hold seats" and "take payment" concerns stay decoupled: BookingService
 * doesn't know or care how payment happens, it just exposes confirmBooking()
 * as the thing that runs once payment succeeds.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    // Simulated failure trigger for testing the failure path without a real
    // gateway: any booking total above this "fails". Replace with real PSP
    // client call when/if you integrate one - not required for this assignment.
    private static final BigDecimal SIMULATED_FAILURE_THRESHOLD = new BigDecimal("100000");

    private final PaymentRepository paymentRepository;
    private final BookingService bookingService;

    @Transactional
    public Payment processPayment(Long bookingId, Long userId) {
        // Reuses BookingService's ownership check (404, not 403, for someone
        // else's booking - see BookingService.getBookingForUser) instead of
        // duplicating that logic here.
        Booking booking = bookingService.getBookingForUser(bookingId, userId);

        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new InvalidBookingStateException(
                    "Booking " + bookingId + " is not awaiting payment (current status: " + booking.getStatus() + ")");
        }

        // Guard against the race where the hold has technically expired but the
        // sweep job (runs every 30s, see SeatHoldExpiryJob) hasn't caught up yet -
        // don't let a stale hold get "paid for" in that window.
        if (booking.getHoldExpiresAt() != null && LocalDateTime.now().isAfter(booking.getHoldExpiresAt())) {
            throw new InvalidBookingStateException(
                    "Seat hold for booking " + bookingId + " has expired - please select seats again");
        }

        boolean success = simulateCharge(booking.getTotalAmount());

        Payment payment = Payment.builder()
                .booking(booking)
                .amount(booking.getTotalAmount())
                .refundedAmount(BigDecimal.ZERO)
                .status(success ? PaymentStatus.SUCCESS : PaymentStatus.FAILED)
                .providerReference("SIMULATED-" + System.currentTimeMillis())
                .createdAt(LocalDateTime.now())
                .build();
        payment = paymentRepository.save(payment);

        if (!success) {
            // Deliberately do NOT touch booking/seat status here. The booking stays
            // PENDING_PAYMENT and the existing hold-expiry job will release the seats
            // naturally if the user doesn't retry in time - avoids two different code
            // paths that can both transition a booking out of PENDING_PAYMENT.
            throw new PaymentFailedException("Payment failed for booking " + bookingId);
        }

        bookingService.confirmBooking(bookingId);
        return payment;
    }

    private boolean simulateCharge(BigDecimal amount) {
        return amount.compareTo(SIMULATED_FAILURE_THRESHOLD) < 0;
    }
}
