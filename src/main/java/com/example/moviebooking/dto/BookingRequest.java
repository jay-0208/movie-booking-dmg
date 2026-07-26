package com.example.moviebooking.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

// Incoming payload for POST /api/bookings (the "hold seats" step).
// userId is deliberately NOT here - it comes from the authenticated principal
// in the controller, never trust a client-supplied user id for who's booking.
public record BookingRequest(
        @NotNull(message = "showId is required")
        Long showId,

        @NotEmpty(message = "At least one seat must be selected")
        List<Long> seatIds,

        // optional - null/blank means no discount applied
        String discountCode
) {
}
