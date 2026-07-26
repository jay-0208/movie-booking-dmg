package com.example.moviebooking.service;

import com.example.moviebooking.entity.*;
import com.example.moviebooking.repository.DiscountCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Handles per-seat pricing (tier + weekend multipliers, which stack
 * multiplicatively - a premium seat on a weekend show gets both) and discount
 * code application.
 *
 * Design decisions, documented here since the spec was silent on them:
 *   - Weekend and premium multipliers stack (both apply to the same seat).
 *     Alternative: take the max of the two instead of multiplying - if you'd
 *     rather do that, it's a one-line change in calculateTotal below.
 *   - Only one discount code per booking (no stacking codes).
 *   - A discount code's redemption count is incremented at HOLD time, not at
 *     payment confirmation. This means an expired, never-paid hold still
 *     "spends" one redemption and it is not given back - simplest correct
 *     behavior for the 48h scope, called out as a known limitation. The
 *     alternative (increment on confirm, decrement on cancel/expiry) is more
 *     correct but adds more state to track for comparatively little payoff here.
 */
@Service
@RequiredArgsConstructor
public class PricingService {

    private final DiscountCodeRepository discountCodeRepository;

    @Value("${app.pricing.weekend-multiplier}")
    private BigDecimal weekendMultiplier;

    @Value("${app.pricing.premium-multiplier}")
    private BigDecimal premiumMultiplier;

    // Called from within BookingService.holdSeats(), which is already
    // @Transactional - the pessimistic lock taken below in applyDiscount()
    // is held for the rest of that same transaction, same as the ShowSeat lock.
    public BigDecimal calculateTotal(Show show, List<ShowSeat> showSeats, String discountCode) {
        BigDecimal total = BigDecimal.ZERO;
        boolean isWeekend = isWeekend(show.getStartTime());

        for (ShowSeat ss : showSeats) {
            BigDecimal seatPrice = show.getBasePrice();
            if (ss.getSeat().getTier() == SeatTier.PREMIUM) {
                seatPrice = seatPrice.multiply(premiumMultiplier);
            }
            if (isWeekend) {
                seatPrice = seatPrice.multiply(weekendMultiplier);
            }
            total = total.add(seatPrice);
        }

        if (discountCode != null && !discountCode.isBlank()) {
            total = applyDiscount(total, discountCode);
        }

        return total.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private BigDecimal applyDiscount(BigDecimal total, String code) {
        DiscountCode dc = discountCodeRepository.findByCodeAndActiveTrueForUpdate(code)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or inactive discount code"));

        LocalDateTime now = LocalDateTime.now();
        if (dc.getValidFrom() != null && now.isBefore(dc.getValidFrom())) {
            throw new IllegalArgumentException("Discount code not yet valid");
        }
        if (dc.getValidUntil() != null && now.isAfter(dc.getValidUntil())) {
            throw new IllegalArgumentException("Discount code expired");
        }
        if (dc.getMaxRedemptions() != null && dc.getTimesRedeemed() != null
                && dc.getTimesRedeemed() >= dc.getMaxRedemptions()) {
            throw new IllegalArgumentException("Discount code redemption limit reached");
        }

        if (dc.getPercentageOff() != null) {
            BigDecimal discount = total.multiply(dc.getPercentageOff())
                    .divide(BigDecimal.valueOf(100));
            total = total.subtract(discount);
        } else if (dc.getFlatAmountOff() != null) {
            total = total.subtract(dc.getFlatAmountOff());
        }

        dc.setTimesRedeemed(dc.getTimesRedeemed() == null ? 1 : dc.getTimesRedeemed() + 1);
        discountCodeRepository.save(dc);

        return total.max(BigDecimal.ZERO);
    }

    private boolean isWeekend(LocalDateTime dateTime) {
        DayOfWeek day = dateTime.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }
}
