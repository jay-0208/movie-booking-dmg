package com.example.moviebooking.exception;

// Generic 404 for admin CRUD lookups (city/theater/screen/movie/show/discount
// code/refund policy not found). Kept separate from BookingNotFoundException
// since that one carries booking-specific ownership semantics.
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
