package com.example.moviebooking.dto;

import com.example.moviebooking.entity.SeatStatus;
import com.example.moviebooking.entity.SeatTier;

// One row per seat for GET /api/shows/{showId}/seats - what the client
// renders as the seat map.
public record SeatAvailabilityResponse(
        Long seatId,
        String rowLabel,
        Integer seatNumber,
        SeatTier tier,
        SeatStatus status
) {
}
