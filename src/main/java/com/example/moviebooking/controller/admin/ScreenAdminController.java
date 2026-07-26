package com.example.moviebooking.controller.admin;

import com.example.moviebooking.dto.ScreenRequest;
import com.example.moviebooking.dto.ScreenResponse;
import com.example.moviebooking.dto.SeatLayoutRequest;
import com.example.moviebooking.entity.Screen;
import com.example.moviebooking.entity.Seat;
import com.example.moviebooking.entity.Theater;
import com.example.moviebooking.exception.ResourceNotFoundException;
import com.example.moviebooking.repository.ScreenRepository;
import com.example.moviebooking.repository.SeatRepository;
import com.example.moviebooking.repository.TheaterRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/admin/screens")
@RequiredArgsConstructor
public class ScreenAdminController {

    private final ScreenRepository screenRepository;
    private final TheaterRepository theaterRepository;
    private final SeatRepository seatRepository;

    @PostMapping
    public ResponseEntity<ScreenResponse> create(@Valid @RequestBody ScreenRequest request) {
        Theater theater = theaterRepository.findById(request.theaterId())
                .orElseThrow(() -> new ResourceNotFoundException("Theater not found: " + request.theaterId()));

        Screen screen = Screen.builder().name(request.name()).theater(theater).build();
        screen = screenRepository.save(screen);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(screen));
    }

    @GetMapping("/{id}")
    public ScreenResponse get(@PathVariable Long id) {
        return toResponse(findOrThrow(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        screenRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // Bulk-creates the physical Seat rows for a screen from a row spec, e.g.
    // POST /api/admin/screens/5/seat-layout
    // { "rows": [ {"rowLabel":"A","seatCount":10,"tier":"PREMIUM"}, ... ] }
    // Idempotency note: calling this twice for the same screen will attempt
    // duplicate seats and violate the (screen_id, row_label, seat_number)
    // unique constraint on Seat - by design, so you can't silently double the
    // layout. If you need to change a layout, delete and recreate the screen,
    // or add a dedicated "replace layout" endpoint if you have time.
    @PostMapping("/{id}/seat-layout")
    public ResponseEntity<List<Seat>> createSeatLayout(@PathVariable Long id,
                                                         @Valid @RequestBody SeatLayoutRequest request) {
        Screen screen = findOrThrow(id);

        List<Seat> seats = new ArrayList<>();
        for (SeatLayoutRequest.RowSpec row : request.rows()) {
            for (int seatNumber = 1; seatNumber <= row.seatCount(); seatNumber++) {
                seats.add(Seat.builder()
                        .screen(screen)
                        .rowLabel(row.rowLabel())
                        .seatNumber(seatNumber)
                        .tier(row.tier())
                        .build());
            }
        }
        seats = seatRepository.saveAll(seats);
        return ResponseEntity.status(HttpStatus.CREATED).body(seats);
    }

    @GetMapping("/{id}/seats")
    public List<Seat> listSeats(@PathVariable Long id) {
        findOrThrow(id); // 404 if screen doesn't exist, rather than silently returning []
        return seatRepository.findByScreenId(id);
    }

    private Screen findOrThrow(Long id) {
        return screenRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Screen not found: " + id));
    }

    private ScreenResponse toResponse(Screen s) {
        return new ScreenResponse(s.getId(), s.getName(), s.getTheater().getId(), s.getTheater().getName());
    }
}
