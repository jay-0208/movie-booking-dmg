package com.example.moviebooking.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

// THE key concurrency entity. One row per (show, seat). This is what you lock
// (SELECT ... FOR UPDATE) when a user tries to hold/book a seat, so two users
// racing for seat A12 on the same show serialize correctly instead of double-booking.
@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"show_id", "seat_id"}))
public class ShowSeat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatStatus status;

    // Set when status = HELD. A scheduled job sweeps expired holds back to AVAILABLE.
    private LocalDateTime holdExpiresAt;

    // Which booking currently owns this seat (hold or confirmed). Null if AVAILABLE.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    // Optimistic lock as a second line of defense on top of pessimistic row locking
    // in the service layer - see BookingService.
    @Version
    private Long version;
}
