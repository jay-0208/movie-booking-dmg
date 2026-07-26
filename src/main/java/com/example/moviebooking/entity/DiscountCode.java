package com.example.moviebooking.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DiscountCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    // Percentage (0-100) OR flat amount - pick one convention and document it.
    private BigDecimal percentageOff;
    private BigDecimal flatAmountOff;

    private LocalDateTime validFrom;
    private LocalDateTime validUntil;

    private Integer maxRedemptions;
    private Integer timesRedeemed;

    private Boolean active;
}
