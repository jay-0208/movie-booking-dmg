package com.example.moviebooking.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        List<String> fieldErrors // populated only for validation failures, else empty
) {
}
