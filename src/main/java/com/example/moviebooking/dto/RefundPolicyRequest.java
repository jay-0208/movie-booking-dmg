package com.example.moviebooking.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record RefundPolicyRequest(
        @NotBlank String name,
        @NotNull @PositiveOrZero Integer minHoursBeforeShow,
        @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal refundPercentage,
        Boolean active
) {
}
