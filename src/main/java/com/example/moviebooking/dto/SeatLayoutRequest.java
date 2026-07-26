package com.example.moviebooking.dto;

import com.example.moviebooking.entity.SeatTier;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

// Bulk seat-layout creation for a screen, e.g.
// { "rows": [ {"rowLabel":"A","seatCount":10,"tier":"PREMIUM"},
//             {"rowLabel":"B","seatCount":12,"tier":"REGULAR"} ] }
// -> creates seats A1..A10 (PREMIUM) and B1..B12 (REGULAR).
public record SeatLayoutRequest(
        @NotEmpty List<@Valid RowSpec> rows
) {
    public record RowSpec(
            @NotBlank String rowLabel,
            @NotNull @Positive Integer seatCount,
            @NotNull SeatTier tier
    ) {
    }
}
