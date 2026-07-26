package com.example.moviebooking.entity;

import jakarta.persistence.*;
import lombok.*;

// A physical auditorium inside a theater. Seat layout is defined once here
// and reused across every Show scheduled on this screen.
@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Screen {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name; // e.g. "Screen 1", "IMAX"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "theater_id", nullable = false)
    private Theater theater;
}
