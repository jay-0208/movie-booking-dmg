package com.example.moviebooking.service;

import com.example.moviebooking.entity.Booking;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

// STUB - deliberately not blocking the booking flow, per the "without
// blocking the booking flow" requirement. @Async runs this on a separate
// thread pool (configured in application.yml under spring.task.execution).
// Swap the log lines for a real email/SMS client if you want extra polish -
// not required, since UI/external integrations are out of scope.
@Service
public class NotificationService {

    @Async
    public void sendBookingConfirmation(Booking booking) {
        // simulate latency of a real notification provider
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.printf("[notification] Booking #%d confirmed for user %s%n",
                booking.getId(), booking.getUser().getEmail());
    }

    @Async
    public void sendCancellationNotice(Booking booking) {
        System.out.printf("[notification] Booking #%d cancelled for user %s%n",
                booking.getId(), booking.getUser().getEmail());
    }
}
