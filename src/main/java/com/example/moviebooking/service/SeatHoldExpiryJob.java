package com.example.moviebooking.service;

import com.example.moviebooking.entity.SeatStatus;
import com.example.moviebooking.entity.ShowSeat;
import com.example.moviebooking.entity.BookingStatus;
import com.example.moviebooking.repository.ShowSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Sweeps expired seat holds back to AVAILABLE and marks the associated
// booking EXPIRED. Runs on a fixed interval - fine for a take-home; at real
// scale you'd rather do this with a per-hold delayed job/TTL than a poll loop.
@Component
@RequiredArgsConstructor
public class SeatHoldExpiryJob {

    private final ShowSeatRepository showSeatRepository;

    @Scheduled(fixedRate = 30_000) // every 30s
    @Transactional
    public void releaseExpiredHolds() {
        List<ShowSeat> expired = showSeatRepository.findExpiredHolds();
        for (ShowSeat ss : expired) {
            ss.setStatus(SeatStatus.AVAILABLE);
            ss.setHoldExpiresAt(null);
            if (ss.getBooking() != null && ss.getBooking().getStatus() == BookingStatus.PENDING_PAYMENT) {
                ss.getBooking().setStatus(BookingStatus.EXPIRED);
            }
            ss.setBooking(null);
        }
        showSeatRepository.saveAll(expired);
    }
}
