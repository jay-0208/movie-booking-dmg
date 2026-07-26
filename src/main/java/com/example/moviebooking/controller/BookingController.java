package com.example.moviebooking.controller;

import com.example.moviebooking.dto.BookingMapper;
import com.example.moviebooking.dto.BookingRequest;
import com.example.moviebooking.dto.BookingResponse;
import com.example.moviebooking.dto.SeatAvailabilityResponse;
import com.example.moviebooking.entity.Booking;
import com.example.moviebooking.entity.ShowSeat;
import com.example.moviebooking.entity.User;
import com.example.moviebooking.repository.ShowSeatRepository;
import com.example.moviebooking.repository.UserRepository;
import com.example.moviebooking.service.BookingService;
import com.example.moviebooking.service.CancellationService;
import com.example.moviebooking.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Customer-facing booking flow. Relies on Spring's default open-in-view
 * (session stays open for the request) so BookingMapper can lazily touch
 * booking.getShow().getMovie() etc. after the @Transactional service method
 * returns. That's fine for this assignment's scope - if you want to turn
 * open-in-view off (it's generally considered better practice), switch the
 * mapper calls to happen inside @Transactional service methods instead and
 * mention the tradeoff in your README.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final PaymentService paymentService;
    private final CancellationService cancellationService;
    private final ShowSeatRepository showSeatRepository;
    private final UserRepository userRepository;

    @GetMapping("/shows/{showId}/seats")
    public ResponseEntity<List<SeatAvailabilityResponse>> getSeatAvailability(@PathVariable Long showId) {
        List<ShowSeat> showSeats = showSeatRepository.findByShowId(showId);
        List<SeatAvailabilityResponse> response = showSeats.stream()
                .map(BookingMapper::toSeatAvailability)
                .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/bookings")
    public ResponseEntity<BookingResponse> createBooking(@Valid @RequestBody BookingRequest request,
                                                           Authentication authentication) {
        Long userId = currentUserId(authentication);
        Booking booking = bookingService.holdSeats(userId, request.showId(), request.seatIds(), request.discountCode());
        return ResponseEntity.status(HttpStatus.CREATED).body(BookingMapper.toResponse(booking));
    }

    @PostMapping("/bookings/{id}/confirm")
    public ResponseEntity<BookingResponse> confirmBooking(@PathVariable Long id, Authentication authentication) {
        Long userId = currentUserId(authentication);
        // processPayment does its own ownership check (see PaymentService ->
        // BookingService.getBookingForUser) and calls bookingService.confirmBooking
        // internally once the simulated charge succeeds.
        paymentService.processPayment(id, userId);
        Booking confirmed = bookingService.getBookingForUser(id, userId);
        return ResponseEntity.ok(BookingMapper.toResponse(confirmed));
    }

    @DeleteMapping("/bookings/{id}")
    public ResponseEntity<BookingResponse> cancelBooking(@PathVariable Long id, Authentication authentication) {
        Long userId = currentUserId(authentication);
        Booking cancelled = cancellationService.cancelBooking(id, userId);
        return ResponseEntity.ok(BookingMapper.toResponse(cancelled));
    }

    @GetMapping("/bookings/{id}")
    public ResponseEntity<BookingResponse> getBooking(@PathVariable Long id, Authentication authentication) {
        Long userId = currentUserId(authentication);
        Booking booking = bookingService.getBookingForUser(id, userId);
        return ResponseEntity.ok(BookingMapper.toResponse(booking));
    }

    @GetMapping("/bookings")
    public ResponseEntity<List<BookingResponse>> getMyBookings(Authentication authentication) {
        Long userId = currentUserId(authentication);
        List<BookingResponse> response = bookingService.getBookingsForUser(userId).stream()
                .map(BookingMapper::toResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    // Spring Security's Authentication#getName() is the username - here, email
    // (see SecurityConfig / User entity). Resolve it to our internal User id.
    private Long currentUserId(Authentication authentication) {
        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
        return user.getId();
    }
}
