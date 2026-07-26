package com.example.moviebooking.entity;

import jakarta.persistence.*;
import lombok.*;

// A physical seat on a screen. This is layout metadata only - booking status
// lives on ShowSeat because the same seat has different availability per show.
@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"screen_id", "row_label", "seat_number"}))
public class Seat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "screen_id", nullable = false)
    private Screen screen;

    @Column(name = "row_label", nullable = false)
    private String rowLabel; // e.g. "A"

    @Column(name = "seat_number", nullable = false)
    private Integer seatNumber; // e.g. 12 -> seat "A12"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatTier tier;
}
