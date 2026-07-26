package com.example.moviebooking.service;

import com.example.moviebooking.entity.*;
import com.example.moviebooking.exception.InvalidBookingStateException;
import com.example.moviebooking.repository.BookingRepository;
import com.example.moviebooking.repository.PaymentRepository;
import com.example.moviebooking.repository.RefundPolicyRepository;
import com.example.moviebooking.repository.ShowSeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Plain Mockito unit tests - BookingService is mocked here rather than real,
// since CancellationService only calls its ownership-checked getter
// (getBookingForUser), not any of its transactional seat-locking logic.
class CancellationServiceTest {

    @Mock private BookingService bookingService;
    @Mock private BookingRepository bookingRepository;
    @Mock private ShowSeatRepository showSeatRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private RefundPolicyRepository refundPolicyRepository;
    @Mock private NotificationService notificationService;

    private CancellationService cancellationService;

    private static final Long BOOKING_ID = 1L;
    private static final Long USER_ID = 10L;

    // Three tiers: >=24h -> 100%, >=2h -> 50%, >=0h -> 0%
    private final List<RefundPolicy> tiers = List.of(
            policy(24, "100"),
            policy(2, "50"),
            policy(0, "0")
    );

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        cancellationService = new CancellationService(
                bookingService, bookingRepository, showSeatRepository,
                paymentRepository, refundPolicyRepository, notificationService);

        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(showSeatRepository.findByBookingId(BOOKING_ID)).thenReturn(List.of());
    }

    @Test
    void cancellingWellBeforeShow_getsFullRefund() {
        Booking booking = confirmedBooking(showIn(30));
        Payment payment = paymentFor(new BigDecimal("500"));
        stub(booking, payment);

        Booking result = cancellationService.cancelBooking(BOOKING_ID, USER_ID);

        assertThat(payment.getRefundedAmount()).isEqualByComparingTo("500.00");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(result.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void cancellingInMiddleWindow_getsPartialRefund() {
        Booking booking = confirmedBooking(showIn(5)); // matches the 2h tier, not 24h
        Payment payment = paymentFor(new BigDecimal("500"));
        stub(booking, payment);

        cancellationService.cancelBooking(BOOKING_ID, USER_ID);

        assertThat(payment.getRefundedAmount()).isEqualByComparingTo("250.00");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
    }

    @Test
    void cancellingRightBeforeShow_getsZeroRefund() {
        Booking booking = confirmedBooking(showIn(0)); // < 2h, falls to the 0h/0% tier
        Payment payment = paymentFor(new BigDecimal("500"));
        stub(booking, payment);

        cancellationService.cancelBooking(BOOKING_ID, USER_ID);

        assertThat(payment.getRefundedAmount()).isEqualByComparingTo("0.00");
        // Zero-refund still resolves to PARTIALLY_REFUNDED - see the comment in
        // CancellationService on why PaymentStatus has no dedicated "no refund" state.
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
    }

    @Test
    void noActiveRefundPolicyConfigured_defaultsToZeroRefund_notFullRefund() {
        Booking booking = confirmedBooking(showIn(30));
        Payment payment = paymentFor(new BigDecimal("500"));
        when(bookingService.getBookingForUser(BOOKING_ID, USER_ID)).thenReturn(booking);
        when(paymentRepository.findByBookingId(BOOKING_ID)).thenReturn(Optional.of(payment));
        when(refundPolicyRepository.findByActiveTrueOrderByMinHoursBeforeShowDesc()).thenReturn(List.of());

        cancellationService.cancelBooking(BOOKING_ID, USER_ID);

        // Fail-closed: no configured policy means no refund, not an accidental 100%.
        assertThat(payment.getRefundedAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void cancellingNonConfirmedBooking_isRejected() {
        Booking booking = confirmedBooking(showIn(30));
        booking.setStatus(BookingStatus.PENDING_PAYMENT);
        when(bookingService.getBookingForUser(BOOKING_ID, USER_ID)).thenReturn(booking);

        assertThatThrownBy(() -> cancellationService.cancelBooking(BOOKING_ID, USER_ID))
                .isInstanceOf(InvalidBookingStateException.class);

        verifyNoInteractions(paymentRepository);
    }

    @Test
    void releasesSeatsBackToAvailable_onCancellation() {
        Booking booking = confirmedBooking(showIn(30));
        Payment payment = paymentFor(new BigDecimal("500"));
        Seat physicalSeat = Seat.builder().rowLabel("A").seatNumber(1).tier(SeatTier.REGULAR).build();
        ShowSeat showSeat = ShowSeat.builder().seat(physicalSeat).status(SeatStatus.BOOKED).booking(booking).build();

        when(bookingService.getBookingForUser(BOOKING_ID, USER_ID)).thenReturn(booking);
        when(paymentRepository.findByBookingId(BOOKING_ID)).thenReturn(Optional.of(payment));
        when(refundPolicyRepository.findByActiveTrueOrderByMinHoursBeforeShowDesc()).thenReturn(tiers);
        when(showSeatRepository.findByBookingId(BOOKING_ID)).thenReturn(List.of(showSeat));

        cancellationService.cancelBooking(BOOKING_ID, USER_ID);

        assertThat(showSeat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(showSeat.getBooking()).isNull();
        verify(showSeatRepository).saveAll(List.of(showSeat));
    }

    // --- helpers ---

    private void stub(Booking booking, Payment payment) {
        when(bookingService.getBookingForUser(BOOKING_ID, USER_ID)).thenReturn(booking);
        when(paymentRepository.findByBookingId(BOOKING_ID)).thenReturn(Optional.of(payment));
        when(refundPolicyRepository.findByActiveTrueOrderByMinHoursBeforeShowDesc()).thenReturn(tiers);
    }

    private Booking confirmedBooking(LocalDateTime showStartTime) {
        Show show = Show.builder().startTime(showStartTime).basePrice(new BigDecimal("500")).build();
        return Booking.builder().id(BOOKING_ID).show(show).status(BookingStatus.CONFIRMED).build();
    }

    private Payment paymentFor(BigDecimal amount) {
        return Payment.builder().amount(amount).refundedAmount(BigDecimal.ZERO).status(PaymentStatus.SUCCESS).build();
    }

    private LocalDateTime showIn(int hours) {
        return LocalDateTime.now().plusHours(hours);
    }

    private static RefundPolicy policy(int minHours, String percentage) {
        return RefundPolicy.builder()
                .minHoursBeforeShow(minHours)
                .refundPercentage(new BigDecimal(percentage))
                .active(true)
                .build();
    }
}
