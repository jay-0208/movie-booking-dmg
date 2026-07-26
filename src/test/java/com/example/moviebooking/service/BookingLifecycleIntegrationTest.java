package com.example.moviebooking.service;

import com.example.moviebooking.entity.*;
import com.example.moviebooking.exception.InvalidBookingStateException;
import com.example.moviebooking.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end coverage of the booking lifecycle against a real (H2) database
 * and real transactions - complements the mocked unit tests, which verify
 * logic in isolation but can't catch things like a wrong @Query or a missed
 * cascade.
 *
 * Isolation: @Transactional on the test class wraps each test method (and
 * its @BeforeEach) in one transaction that Spring's TestContext framework
 * rolls back automatically afterward - no data survives between test
 * methods. This class previously used @DirtiesContext to reset state
 * instead, which does NOT actually solve that problem here: the H2 URL in
 * application.yml uses DB_CLOSE_DELAY=-1 to keep the in-memory database
 * alive for the app's whole lifetime, so a fresh Spring context still
 * connects to the SAME underlying database - meaning setUp()'s
 * user@test.com insert from test 1 was still there when test 2 ran,
 * producing a unique-constraint violation. @Transactional rollback sidesteps
 * this entirely (nothing is ever committed, so there's nothing to collide
 * with) and is faster besides, since it avoids a full context reload
 * between every test method.
 */
@SpringBootTest
@Transactional
class BookingLifecycleIntegrationTest {

    @Autowired private BookingService bookingService;
    @Autowired private PaymentService paymentService;
    @Autowired private CancellationService cancellationService;
    @Autowired private SeatHoldExpiryJob seatHoldExpiryJob;

    @Autowired private UserRepository userRepository;
    @Autowired private CityRepository cityRepository;
    @Autowired private TheaterRepository theaterRepository;
    @Autowired private ScreenRepository screenRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private MovieRepository movieRepository;
    @Autowired private ShowRepository showRepository;
    @Autowired private ShowSeatRepository showSeatRepository;
    @Autowired private BookingRepository bookingRepository;

    private User user;
    private Long showId;
    private Long seatId;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .email("lifecycle@test.com").passwordHash("x").name("Test User").role(Role.CUSTOMER).build());

        City city = cityRepository.save(City.builder().name("Indore").state("MP").build());
        Theater theater = theaterRepository.save(Theater.builder().name("PVR").address("MG Road").city(city).build());
        Screen screen = screenRepository.save(Screen.builder().name("Screen 1").theater(theater).build());
        Seat seat = seatRepository.save(Seat.builder().screen(screen).rowLabel("A").seatNumber(1).tier(SeatTier.REGULAR).build());
        Movie movie = movieRepository.save(Movie.builder().title("Test Movie").language("EN").durationMinutes(120).build());
        Show show = showRepository.save(Show.builder()
                .movie(movie).screen(screen)
                .startTime(LocalDateTime.now().plusDays(2))
                .endTime(LocalDateTime.now().plusDays(2).plusHours(2))
                .basePrice(new BigDecimal("200"))
                .build());
        showSeatRepository.save(ShowSeat.builder().show(show).seat(seat).status(SeatStatus.AVAILABLE).build());

        showId = show.getId();
        seatId = seat.getId();
    }

    @Test
    void happyPath_holdThenConfirm_seatEndsUpBookedAndVisibleInHistory() {
        Booking booking = bookingService.holdSeats(user.getId(), showId, List.of(seatId), null);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);

        ShowSeat afterHold = showSeatRepository.findByShowId(showId).get(0);
        assertThat(afterHold.getStatus()).isEqualTo(SeatStatus.HELD);

        paymentService.processPayment(booking.getId(), user.getId());

        Booking confirmed = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(confirmed.getStatus()).isEqualTo(BookingStatus.CONFIRMED);

        ShowSeat afterConfirm = showSeatRepository.findByShowId(showId).get(0);
        assertThat(afterConfirm.getStatus()).isEqualTo(SeatStatus.BOOKED);

        List<Booking> history = bookingService.getBookingsForUser(user.getId());
        assertThat(history).extracting(Booking::getId).contains(booking.getId());
    }

    @Test
    void expiryPath_expiredHold_isSweptBackToAvailable_andCannotBePaidFor() {
        Booking booking = bookingService.holdSeats(user.getId(), showId, List.of(seatId), null);

        // Simulate time passing by directly backdating the hold rather than
        // sleeping the test thread for real minutes.
        booking.setHoldExpiresAt(LocalDateTime.now().minusMinutes(1));
        bookingRepository.save(booking);
        ShowSeat showSeat = showSeatRepository.findByShowId(showId).get(0);
        showSeat.setHoldExpiresAt(LocalDateTime.now().minusMinutes(1));
        showSeatRepository.save(showSeat);

        seatHoldExpiryJob.releaseExpiredHolds();

        ShowSeat afterSweep = showSeatRepository.findByShowId(showId).get(0);
        assertThat(afterSweep.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(afterSweep.getBooking()).isNull();

        Booking afterSweepBooking = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(afterSweepBooking.getStatus()).isEqualTo(BookingStatus.EXPIRED);

        // A second user can now successfully hold the freed-up seat.
        User secondUser = userRepository.save(User.builder()
                .email("second@test.com").passwordHash("x").name("Second User").role(Role.CUSTOMER).build());
        Booking secondBooking = bookingService.holdSeats(secondUser.getId(), showId, List.of(seatId), null);
        assertThat(secondBooking.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
    }

    @Test
    void cancelPath_confirmedBooking_releasesSeatAndRefunds() {
        Booking booking = bookingService.holdSeats(user.getId(), showId, List.of(seatId), null);
        paymentService.processPayment(booking.getId(), user.getId());

        Booking cancelled = cancellationService.cancelBooking(booking.getId(), user.getId());
        assertThat(cancelled.getStatus()).isEqualTo(BookingStatus.CANCELLED);

        ShowSeat afterCancel = showSeatRepository.findByShowId(showId).get(0);
        assertThat(afterCancel.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
    }

    @Test
    void cannotConfirmSameBookingTwice() {
        Booking booking = bookingService.holdSeats(user.getId(), showId, List.of(seatId), null);
        paymentService.processPayment(booking.getId(), user.getId());

        assertThatThrownBy(() -> paymentService.processPayment(booking.getId(), user.getId()))
                .isInstanceOf(InvalidBookingStateException.class);
    }
}
