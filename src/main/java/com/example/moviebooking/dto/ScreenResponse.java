package com.example.moviebooking.dto;

public record ScreenResponse(
        Long id,
        String name,
        Long theaterId,
        String theaterName
) {
}
