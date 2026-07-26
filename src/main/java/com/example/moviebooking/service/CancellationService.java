package com.example.moviebooking.service;

import com.example.moviebooking.entity.*;
import com.example.moviebooking.exception.InvalidBookingStateException;
import com.example.moviebooking.repository.PaymentRepository;
import com.example.moviebooking.repository.RefundPolicyRepository;
import com.example.moviebooking.repository.BookingRepository;
import com.example.moviebooking.repository.ShowSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Cancellation + refund. Only CONFIRMED bookings go through here - a
 * PENDING_PAYMENT booking isn't "cancelled" in the refund sense, it's just
 * abandoned and left for SeatHoldExpiryJob to release; there's no payment to
 * refund yet, so routing it through this service would be misleading.
 */
@Service
@RequiredArgsConstructor
public class CancellationService {

    private final BookingService bookingService;
    private final BookingRepository bookingRepository;
    private final ShowSeatRepository showSeatRepository;
    private final PaymentRepository paymentRepository;
    private final RefundPolicyRepository refundPolicyRepository;
    private final NotificationService notificationService;

    @Transactional
    public Booking cancelBooking(Long bookingId, Long userId) {
        Booking booking = bookingService.getBookingForUser(bookingId, userId);

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new InvalidBookingStateException(
                    "Booking " + bookingId + " cannot be cancelled (current status: " + booking.getStatus() + ")");
        }

        Payment payment = paymentRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new InvalidBookingStateException(
                        "Booking " + bookingId + " has no associated payment to refund"));

        BigDecimal refundPercentage = resolveRefundPercentage(booking.getShow().getStartTime());
        BigDecimal refundAmount = payment.getAmount()
                .multiply(refundPercentage)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        payment.setRefundedAmount(refundAmount);
        // Zero-refund cancellations (e.g. cancelling minutes before showtime under
        // a policy with a 0% tier) still go through PARTIALLY_REFUNDED rather than
        // a dedicated "no refund" status - PaymentStatus doesn't model that state
        // separately, and refundedAmount = 0 on the record is enough to tell the
        // full story. Called out here since it's a bit of an overload of the enum.
        payment.setStatus(refundAmount.compareTo(payment.getAmount()) >= 0
                ? PaymentStatus.REFUNDED
                : PaymentStatus.PARTIALLY_REFUNDED);
        paymentRepository.save(payment);

        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        List<ShowSeat> showSeats = showSeatRepository.findByBookingId(bookingId);
        for (ShowSeat ss : showSeats) {
            ss.setStatus(SeatStatus.AVAILABLE);
            ss.setHoldExpiresAt(null);
            ss.setBooking(null);
        }
        showSeatRepository.saveAll(showSeats);

        notificationService.sendCancellationNotice(booking);

        return booking;
    }

    // Highest minHoursBeforeShow tier whose threshold is met, wins - e.g. tiers
    // of (24h -> 100%), (2h -> 50%), (0h -> 0%): cancelling 30h out matches the
    // 24h tier, cancelling 5h out matches the 2h tier, cancelling 30min out
    // matches the 0h tier. If no active policy exists at all, default to 0% -
    // fail closed rather than accidentally give a full refund with no configured rule.
    private BigDecimal resolveRefundPercentage(LocalDateTime showStartTime) {
        long hoursUntilShow = Duration.between(LocalDateTime.now(), showStartTime).toHours();

        return refundPolicyRepository.findByActiveTrueOrderByMinHoursBeforeShowDesc().stream()
                .filter(policy -> hoursUntilShow >= policy.getMinHoursBeforeShow())
                .findFirst()
                .map(RefundPolicy::getRefundPercentage)
                .orElse(BigDecimal.ZERO);
    }
}
