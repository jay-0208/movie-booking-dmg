package com.example.moviebooking.repository;

import com.example.moviebooking.entity.Show;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface ShowRepository extends JpaRepository<Show, Long> {
    // Add city/date filters as needed, e.g.:
    List<Show> findByMovieIdAndScreen_Theater_City_IdAndStartTimeBetween(
            Long movieId, Long cityId, LocalDateTime from, LocalDateTime to);
}
