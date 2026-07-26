package com.example.moviebooking.service;

import com.example.moviebooking.entity.*;
import com.example.moviebooking.repository.DiscountCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// Plain Mockito unit tests (no Spring context) - fast, and PricingService has
// no framework-specific behavior worth an integration test here.
class PricingServiceTest {

    @Mock
    private DiscountCodeRepository discountCodeRepository;

    private PricingService pricingService;

    private static final BigDecimal WEEKEND_MULTIPLIER = new BigDecimal("1.5");
    private static final BigDecimal PREMIUM_MULTIPLIER = new BigDecimal("1.8");

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        pricingService = new PricingService(discountCodeRepository);
        ReflectionTestUtils.setField(pricingService, "weekendMultiplier", WEEKEND_MULTIPLIER);
        ReflectionTestUtils.setField(pricingService, "premiumMultiplier", PREMIUM_MULTIPLIER);
    }

    @Test
    void regularSeatOnWeekday_isJustBasePrice() {
        Show show = showAt(nextWeekday(), new BigDecimal("200"));
        ShowSeat seat = showSeat(SeatTier.REGULAR);

        BigDecimal total = pricingService.calculateTotal(show, List.of(seat), null);

        assertThat(total).isEqualByComparingTo("200.00");
    }

    @Test
    void premiumSeatOnWeekday_appliesPremiumMultiplierOnly() {
        Show show = showAt(nextWeekday(), new BigDecimal("200"));
        ShowSeat seat = showSeat(SeatTier.PREMIUM);

        BigDecimal total = pricingService.calculateTotal(show, List.of(seat), null);

        // 200 * 1.8
        assertThat(total).isEqualByComparingTo("360.00");
    }

    @Test
    void regularSeatOnWeekend_appliesWeekendMultiplierOnly() {
        Show show = showAt(nextWeekend(), new BigDecimal("200"));
        ShowSeat seat = showSeat(SeatTier.REGULAR);

        BigDecimal total = pricingService.calculateTotal(show, List.of(seat), null);

        // 200 * 1.5
        assertThat(total).isEqualByComparingTo("300.00");
    }

    @Test
    void premiumSeatOnWeekend_stacksBothMultipliers() {
        Show show = showAt(nextWeekend(), new BigDecimal("200"));
        ShowSeat seat = showSeat(SeatTier.PREMIUM);

        BigDecimal total = pricingService.calculateTotal(show, List.of(seat), null);

        // 200 * 1.8 * 1.5 = 540.00
        assertThat(total).isEqualByComparingTo("540.00");
    }

    @Test
    void percentageDiscount_reducesTotalByPercentage() {
        Show show = showAt(nextWeekday(), new BigDecimal("200"));
        ShowSeat seat = showSeat(SeatTier.REGULAR);
        DiscountCode code = discountCode("SAVE10", new BigDecimal("10"), null);
        when(discountCodeRepository.findByCodeAndActiveTrueForUpdate("SAVE10")).thenReturn(Optional.of(code));

        BigDecimal total = pricingService.calculateTotal(show, List.of(seat), "SAVE10");

        // 200 - 10% = 180.00
        assertThat(total).isEqualByComparingTo("180.00");
        assertThat(code.getTimesRedeemed()).isEqualTo(1);
    }

    @Test
    void flatDiscount_reducesTotalByFixedAmount() {
        Show show = showAt(nextWeekday(), new BigDecimal("200"));
        ShowSeat seat = showSeat(SeatTier.REGULAR);
        DiscountCode code = discountCode("FLAT50", null, new BigDecimal("50"));
        when(discountCodeRepository.findByCodeAndActiveTrueForUpdate("FLAT50")).thenReturn(Optional.of(code));

        BigDecimal total = pricingService.calculateTotal(show, List.of(seat), "FLAT50");

        assertThat(total).isEqualByComparingTo("150.00");
    }

    @Test
    void expiredDiscountCode_isRejected() {
        Show show = showAt(nextWeekday(), new BigDecimal("200"));
        ShowSeat seat = showSeat(SeatTier.REGULAR);
        DiscountCode code = discountCode("OLDCODE", new BigDecimal("10"), null);
        code.setValidUntil(LocalDateTime.now().minusDays(1));
        when(discountCodeRepository.findByCodeAndActiveTrueForUpdate("OLDCODE")).thenReturn(Optional.of(code));

        assertThatThrownBy(() -> pricingService.calculateTotal(show, List.of(seat), "OLDCODE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void redemptionLimitReached_isRejected() {
        Show show = showAt(nextWeekday(), new BigDecimal("200"));
        ShowSeat seat = showSeat(SeatTier.REGULAR);
        DiscountCode code = discountCode("MAXEDOUT", new BigDecimal("10"), null);
        code.setMaxRedemptions(5);
        code.setTimesRedeemed(5);
        when(discountCodeRepository.findByCodeAndActiveTrueForUpdate("MAXEDOUT")).thenReturn(Optional.of(code));

        assertThatThrownBy(() -> pricingService.calculateTotal(show, List.of(seat), "MAXEDOUT"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("redemption limit");
    }

    @Test
    void invalidDiscountCode_isRejected() {
        Show show = showAt(nextWeekday(), new BigDecimal("200"));
        ShowSeat seat = showSeat(SeatTier.REGULAR);
        when(discountCodeRepository.findByCodeAndActiveTrueForUpdate("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pricingService.calculateTotal(show, List.of(seat), "NOPE"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- helpers ---

    private Show showAt(LocalDateTime startTime, BigDecimal basePrice) {
        return Show.builder().startTime(startTime).basePrice(basePrice).build();
    }

    private ShowSeat showSeat(SeatTier tier) {
        Seat seat = Seat.builder().rowLabel("A").seatNumber(1).tier(tier).build();
        return ShowSeat.builder().seat(seat).status(SeatStatus.AVAILABLE).build();
    }

    private DiscountCode discountCode(String code, BigDecimal percentageOff, BigDecimal flatAmountOff) {
        return DiscountCode.builder()
                .code(code)
                .percentageOff(percentageOff)
                .flatAmountOff(flatAmountOff)
                .active(true)
                .timesRedeemed(0)
                .build();
    }

    private LocalDateTime nextWeekday() {
        LocalDateTime d = LocalDateTime.now();
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
            d = d.plusDays(1);
        }
        return d;
    }

    private LocalDateTime nextWeekend() {
        LocalDateTime d = LocalDateTime.now();
        while (d.getDayOfWeek() != DayOfWeek.SATURDAY) {
            d = d.plusDays(1);
        }
        return d;
    }
}
