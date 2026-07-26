package com.example.moviebooking.repository;

import com.example.moviebooking.entity.DiscountCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface DiscountCodeRepository extends JpaRepository<DiscountCode, Long> {
    Optional<DiscountCode> findByCodeAndActiveTrue(String code);

    // Same pattern as ShowSeatRepository.findByShowIdAndSeatIdsForUpdate: locks
    // the row for the duration of the enclosing transaction so two concurrent
    // bookings using the same code near its maxRedemptions limit can't both read
    // "redemptions available" and both proceed - the second blocks until the
    // first commits, then sees the updated count.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select dc from DiscountCode dc where dc.code = :code and dc.active = true")
    Optional<DiscountCode> findByCodeAndActiveTrueForUpdate(@Param("code") String code);
}
