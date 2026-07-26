package com.example.moviebooking.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

// Example rule: "cancel >= 24h before showtime -> 100% refund", "2-24h -> 50%", "<2h -> 0%".
// Model it as tiers so admins can configure without a code change.
@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RefundPolicy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer minHoursBeforeShow;

    @Column(nullable = false)
    private BigDecimal refundPercentage; // 0-100

    private Boolean active;
}
