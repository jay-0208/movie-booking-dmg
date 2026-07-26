package com.example.moviebooking.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Exactly one of percentageOff / flatAmountOff should be set - enforced in
// the service (see DiscountCodeAdminController.validateExactlyOneDiscountType),
// not here, since cross-field validation as a bean-validation annotation adds
// more ceremony than it's worth for one rule. Each field's own RANGE is
// still checked here though, e.g. rejecting percentageOff: 500 up front
// instead of letting it reach PricingService and quietly produce a negative total.
public record DiscountCodeRequest(
        @NotBlank String code,
        @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal percentageOff,
        @PositiveOrZero BigDecimal flatAmountOff,
        LocalDateTime validFrom,
        LocalDateTime validUntil,
        @Positive Integer maxRedemptions,
        Boolean active
) {
}
