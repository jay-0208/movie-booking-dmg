package com.example.moviebooking.dto;

import com.example.moviebooking.entity.Booking;
import com.example.moviebooking.entity.ShowSeat;

import java.util.List;

// Plain static mapper - no MapStruct dependency needed for a model this size.
// Keeping mapping logic out of the entities/service keeps BookingService
// focused on business rules, not response shaping.
public final class BookingMapper {

    private BookingMapper() {
    }

    public static BookingResponse toResponse(Booking booking) {
        List<String> seatLabels = booking.getSeats() == null
                ? List.of()
                : booking.getSeats().stream()
                    .map(BookingMapper::seatLabel)
                    .toList();

        return new BookingResponse(
                booking.getId(),
                booking.getStatus(),
                booking.getTotalAmount(),
                booking.getShow().getId(),
                booking.getShow().getMovie().getTitle(),
                booking.getShow().getStartTime(),
                seatLabels,
                booking.getCreatedAt(),
                booking.getHoldExpiresAt()
        );
    }

    public static SeatAvailabilityResponse toSeatAvailability(ShowSeat showSeat) {
        return new SeatAvailabilityResponse(
                showSeat.getSeat().getId(),
                showSeat.getSeat().getRowLabel(),
                showSeat.getSeat().getSeatNumber(),
                showSeat.getSeat().getTier(),
                showSeat.getStatus()
        );
    }

    private static String seatLabel(ShowSeat showSeat) {
        return showSeat.getSeat().getRowLabel() + showSeat.getSeat().getSeatNumber();
    }
}
