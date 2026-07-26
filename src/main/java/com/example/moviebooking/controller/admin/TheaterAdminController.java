package com.example.moviebooking.controller.admin;

import com.example.moviebooking.dto.TheaterRequest;
import com.example.moviebooking.dto.TheaterResponse;
import com.example.moviebooking.entity.City;
import com.example.moviebooking.entity.Theater;
import com.example.moviebooking.exception.ResourceNotFoundException;
import com.example.moviebooking.repository.CityRepository;
import com.example.moviebooking.repository.TheaterRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/theaters")
@RequiredArgsConstructor
public class TheaterAdminController {

    private final TheaterRepository theaterRepository;
    private final CityRepository cityRepository;

    @PostMapping
    public ResponseEntity<TheaterResponse> create(@Valid @RequestBody TheaterRequest request) {
        City city = cityRepository.findById(request.cityId())
                .orElseThrow(() -> new ResourceNotFoundException("City not found: " + request.cityId()));

        Theater theater = Theater.builder()
                .name(request.name())
                .address(request.address())
                .city(city)
                .build();
        theater = theaterRepository.save(theater);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(theater));
    }

    @GetMapping
    public List<TheaterResponse> list(@RequestParam(required = false) Long cityId) {
        List<Theater> theaters = cityId != null
                ? theaterRepository.findByCityId(cityId)
                : theaterRepository.findAll();
        return theaters.stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public TheaterResponse get(@PathVariable Long id) {
        return toResponse(findOrThrow(id));
    }

    @PutMapping("/{id}")
    public TheaterResponse update(@PathVariable Long id, @Valid @RequestBody TheaterRequest request) {
        Theater theater = findOrThrow(id);
        City city = cityRepository.findById(request.cityId())
                .orElseThrow(() -> new ResourceNotFoundException("City not found: " + request.cityId()));
        theater.setName(request.name());
        theater.setAddress(request.address());
        theater.setCity(city);
        return toResponse(theaterRepository.save(theater));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        theaterRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private Theater findOrThrow(Long id) {
        return theaterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Theater not found: " + id));
    }

    private TheaterResponse toResponse(Theater t) {
        return new TheaterResponse(t.getId(), t.getName(), t.getAddress(), t.getCity().getId(), t.getCity().getName());
    }
}
