package com.example.moviebooking.dto;

public record TheaterResponse(
        Long id,
        String name,
        String address,
        Long cityId,
        String cityName
) {
}
