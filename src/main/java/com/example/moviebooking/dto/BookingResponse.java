package com.example.moviebooking.dto;

import com.example.moviebooking.entity.BookingStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record BookingResponse(
        Long id,
        BookingStatus status,
        BigDecimal totalAmount,
        Long showId,
        String movieTitle,
        LocalDateTime showStartTime,
        List<String> seatLabels,   // e.g. ["A1", "A2"]
        LocalDateTime createdAt,
        LocalDateTime holdExpiresAt // null once CONFIRMED
) {
}
