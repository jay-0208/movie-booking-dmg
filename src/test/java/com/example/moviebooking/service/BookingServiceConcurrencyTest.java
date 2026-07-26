package com.example.moviebooking.service;

import com.example.moviebooking.entity.*;
import com.example.moviebooking.exception.SeatUnavailableException;
import com.example.moviebooking.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * THE test that matters most for this assignment: fire N concurrent requests
 * at the same seat and assert exactly one wins. This is what demonstrates the
 * "correctly serialize bookings without double-allocation" requirement -
 * make sure your README and video call this test out explicitly.
 */
@SpringBootTest
class BookingServiceConcurrencyTest {

    @Autowired private BookingService bookingService;
    @Autowired private UserRepository userRepository;
    @Autowired private CityRepository cityRepository;
    @Autowired private TheaterRepository theaterRepository;
    @Autowired private ScreenRepository screenRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private MovieRepository movieRepository;
    @Autowired private ShowRepository showRepository;
    @Autowired private ShowSeatRepository showSeatRepository;

    private Long showId;
    private Long seatId;

    @BeforeEach
    void setUp() {
        City city = cityRepository.save(City.builder().name("Indore").state("MP").build());
        Theater theater = theaterRepository.save(Theater.builder().name("PVR").address("MG Road").city(city).build());
        Screen screen = screenRepository.save(Screen.builder().name("Screen 1").theater(theater).build());
        Seat seat = seatRepository.save(Seat.builder().screen(screen).rowLabel("A").seatNumber(1).tier(SeatTier.REGULAR).build());
        Movie movie = movieRepository.save(Movie.builder().title("Test Movie").language("EN").durationMinutes(120).build());
        Show show = showRepository.save(Show.builder()
                .movie(movie).screen(screen)
                .startTime(LocalDateTime.now().plusDays(1))
                .endTime(LocalDateTime.now().plusDays(1).plusHours(2))
                .basePrice(new BigDecimal("200"))
                .build());
        showSeatRepository.save(ShowSeat.builder().show(show).seat(seat).status(SeatStatus.AVAILABLE).build());

        showId = show.getId();
        seatId = seat.getId();
    }

    @Test
    void onlyOneConcurrentRequestShouldWinTheSameSeat() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();

        List<User> users = createUsers(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final User u = users.get(i);
            executor.submit(() -> {
                try {
                    startLatch.await();
                    bookingService.holdSeats(u.getId(), showId, List.of(seatId), null);
                    successCount.incrementAndGet();
                } catch (SeatUnavailableException e) {
                    failureCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // release all threads at once
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactly one request should have won the seat");
        assertEquals(threadCount - 1, failureCount.get(), "All others should fail with SeatUnavailableException");
    }

    private List<User> createUsers(int n) {
        return java.util.stream.IntStream.range(0, n)
                .mapToObj(i -> userRepository.save(User.builder()
                        .email("user" + i + "@test.com")
                        .passwordHash("x")
                        .name("User " + i)
                        .role(Role.CUSTOMER)
                        .build()))
                .toList();
    }
}
