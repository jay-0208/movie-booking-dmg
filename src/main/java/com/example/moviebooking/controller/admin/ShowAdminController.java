package com.example.moviebooking.controller.admin;

import com.example.moviebooking.dto.ShowRequest;
import com.example.moviebooking.dto.ShowResponse;
import com.example.moviebooking.entity.*;
import com.example.moviebooking.exception.ResourceNotFoundException;
import com.example.moviebooking.repository.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/shows")
@RequiredArgsConstructor
public class ShowAdminController {

    private final ShowRepository showRepository;
    private final MovieRepository movieRepository;
    private final ScreenRepository screenRepository;
    private final SeatRepository seatRepository;
    private final ShowSeatRepository showSeatRepository;

    // Creating a Show immediately bulk-creates one ShowSeat row (status
    // AVAILABLE) per physical Seat on that screen. This is the step that makes
    // the show actually bookable - without it, BookingController's seat
    // availability endpoint would return an empty seat map. Requires the
    // screen's seat layout (ScreenAdminController.createSeatLayout) to already
    // exist; if it doesn't, this creates a Show with zero bookable seats rather
    // than failing outright - documented tradeoff, could equally justify a hard
    // 400 "screen has no seat layout yet" instead.
    @PostMapping
    public ResponseEntity<ShowResponse> create(@Valid @RequestBody ShowRequest request) {
        Movie movie = movieRepository.findById(request.movieId())
                .orElseThrow(() -> new ResourceNotFoundException("Movie not found: " + request.movieId()));
        Screen screen = screenRepository.findById(request.screenId())
                .orElseThrow(() -> new ResourceNotFoundException("Screen not found: " + request.screenId()));

        if (!request.endTime().isAfter(request.startTime())) {
            throw new IllegalArgumentException("endTime must be after startTime");
        }

        Show show = Show.builder()
                .movie(movie)
                .screen(screen)
                .startTime(request.startTime())
                .endTime(request.endTime())
                .basePrice(request.basePrice())
                .build();
        show = showRepository.save(show);

        List<Seat> seats = seatRepository.findByScreenId(screen.getId());
        Show savedShow = show;
        List<ShowSeat> showSeats = seats.stream()
                .map(seat -> ShowSeat.builder()
                        .show(savedShow)
                        .seat(seat)
                        .status(SeatStatus.AVAILABLE)
                        .build())
                .toList();
        showSeatRepository.saveAll(showSeats);

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(show));
    }

    @GetMapping
    public List<ShowResponse> list() {
        return showRepository.findAll().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public ShowResponse get(@PathVariable Long id) {
        return toResponse(findOrThrow(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        // Deliberately not checking for existing bookings before deleting - a
        // real system would block/cascade-cancel here. Out of scope for this
        // take-home but worth a line in "What's Not Done" in your README.
        showRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private Show findOrThrow(Long id) {
        return showRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Show not found: " + id));
    }

    private ShowResponse toResponse(Show s) {
        return new ShowResponse(
                s.getId(),
                s.getMovie().getId(),
                s.getMovie().getTitle(),
                s.getScreen().getId(),
                s.getScreen().getName(),
                s.getScreen().getTheater().getName(),
                s.getStartTime(),
                s.getEndTime(),
                s.getBasePrice()
        );
    }
}
