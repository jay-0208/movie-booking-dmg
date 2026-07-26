package com.example.moviebooking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record MovieRequest(
        @NotBlank String title,
        String language,
        @Positive Integer durationMinutes,
        String genre
) {
}
