package com.example.moviebooking.dto;

import jakarta.validation.constraints.NotBlank;

public record CityRequest(
        @NotBlank String name,
        @NotBlank String state
) {
}
