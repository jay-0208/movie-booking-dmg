package com.example.moviebooking.controller.admin;

import com.example.moviebooking.dto.MovieRequest;
import com.example.moviebooking.entity.Movie;
import com.example.moviebooking.exception.ResourceNotFoundException;
import com.example.moviebooking.repository.MovieRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Movie has no relations either - entity doubles as the response, same
// reasoning as CityAdminController. Note this endpoint lives under
// /api/admin/movies (create/update/delete), separate from a future public,
// read-only /api/movies browsing endpoint (permitAll in SecurityConfig) -
// don't confuse the two paths.
@RestController
@RequestMapping("/api/admin/movies")
@RequiredArgsConstructor
public class MovieAdminController {

    private final MovieRepository movieRepository;

    @PostMapping
    public ResponseEntity<Movie> create(@Valid @RequestBody MovieRequest request) {
        Movie movie = Movie.builder()
                .title(request.title())
                .language(request.language())
                .durationMinutes(request.durationMinutes())
                .genre(request.genre())
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(movieRepository.save(movie));
    }

    @GetMapping
    public List<Movie> list() {
        return movieRepository.findAll();
    }

    @GetMapping("/{id}")
    public Movie get(@PathVariable Long id) {
        return findOrThrow(id);
    }

    @PutMapping("/{id}")
    public Movie update(@PathVariable Long id, @Valid @RequestBody MovieRequest request) {
        Movie movie = findOrThrow(id);
        movie.setTitle(request.title());
        movie.setLanguage(request.language());
        movie.setDurationMinutes(request.durationMinutes());
        movie.setGenre(request.genre());
        return movieRepository.save(movie);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        movieRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private Movie findOrThrow(Long id) {
        return movieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Movie not found: " + id));
    }
}
