package com.example.moviebooking.entity;

// Status of a seat FOR A SPECIFIC SHOW (not globally - the same physical seat
// is AVAILABLE for a 9pm show and BOOKED for a 6pm show independently).
public enum SeatStatus {
    AVAILABLE,
    HELD,
    BOOKED
}
