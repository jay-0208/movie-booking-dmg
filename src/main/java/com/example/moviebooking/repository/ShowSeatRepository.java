package com.example.moviebooking.repository;

import com.example.moviebooking.entity.ShowSeat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ShowSeatRepository extends JpaRepository<ShowSeat, Long> {

    List<ShowSeat> findByShowId(Long showId);

    List<ShowSeat> findByBookingId(Long bookingId);

    // Pessimistic write lock: this is what prevents two concurrent requests from
    // both reading "AVAILABLE" and both proceeding to hold/book the same seat.
    // The second transaction blocks here until the first commits or rolls back.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ss from ShowSeat ss where ss.show.id = :showId and ss.seat.id in :seatIds")
    List<ShowSeat> findByShowIdAndSeatIdsForUpdate(@Param("showId") Long showId, @Param("seatIds") List<Long> seatIds);

    @Query("select ss from ShowSeat ss where ss.status = com.example.moviebooking.entity.SeatStatus.HELD and ss.holdExpiresAt < CURRENT_TIMESTAMP")
    List<ShowSeat> findExpiredHolds();
}
