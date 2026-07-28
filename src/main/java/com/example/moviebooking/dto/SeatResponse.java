package com.example.moviebooking.dto;

import com.example.moviebooking.entity.SeatTier;

// Response shape for admin seat endpoints (bulk seat-layout creation and
// listing seats on a screen). Deliberately does NOT include the Screen
// reference: unlike ScreenResponse/TheaterResponse (which need to show which
// parent they belong to), a seat listing is always already scoped to one
// screen via the URL path (/api/admin/screens/{id}/seats), so echoing
// screenId back would be redundant, not informative.
public record SeatResponse(
        Long id,
        String rowLabel,
        Integer seatNumber,
        SeatTier tier
) {
}