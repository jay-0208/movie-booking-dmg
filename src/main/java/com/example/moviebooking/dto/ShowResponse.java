package com.example.moviebooking.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ShowResponse(
        Long id,
        Long movieId,
        String movieTitle,
        Long screenId,
        String screenName,
        String theaterName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        BigDecimal basePrice
) {
}
