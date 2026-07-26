package com.example.moviebooking.controller.admin;

import com.example.moviebooking.dto.CityRequest;
import com.example.moviebooking.entity.City;
import com.example.moviebooking.exception.ResourceNotFoundException;
import com.example.moviebooking.repository.CityRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// City has no relations to worry about serializing, so the entity doubles as
// the response body here - a dedicated CityResponse DTO would be pure
// boilerplate. Compare to Theater/Screen/Show below, which DO need DTOs
// because their ManyToOne relations would otherwise hit lazy-proxy
// serialization issues.
@RestController
@RequestMapping("/api/admin/cities")
@RequiredArgsConstructor
public class CityAdminController {

    private final CityRepository cityRepository;

    @PostMapping
    public ResponseEntity<City> create(@Valid @RequestBody CityRequest request) {
        City city = City.builder().name(request.name()).state(request.state()).build();
        return ResponseEntity.status(HttpStatus.CREATED).body(cityRepository.save(city));
    }

    @GetMapping
    public List<City> list() {
        return cityRepository.findAll();
    }

    @GetMapping("/{id}")
    public City get(@PathVariable Long id) {
        return cityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("City not found: " + id));
    }

    @PutMapping("/{id}")
    public City update(@PathVariable Long id, @Valid @RequestBody CityRequest request) {
        City city = get(id);
        city.setName(request.name());
        city.setState(request.state());
        return cityRepository.save(city);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        cityRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
