package com.example.moviebooking.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    private BigDecimal amount;
    private BigDecimal refundedAmount;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private String providerReference; // stub - you're not integrating a real PSP

    private LocalDateTime createdAt;
}
