package com.bentork.ev_system.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bentork.ev_system.enums.BookingStatus;
import com.bentork.ev_system.model.Slot;
import com.bentork.ev_system.model.SlotBooking;
import com.bentork.ev_system.repository.SlotBookingRepository;
import com.bentork.ev_system.repository.SlotRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Scheduled cleanup service that automatically expires slot bookings
 * where the slot's end time has passed and the booking status is still
 * "booked".
 * 
 * This prevents "ghost bookings" from blocking charger slots
 * when users book a slot but never show up.
 * 
 * Runs every 5 minutes.
 */
@Slf4j
@Service
public class SlotBookingCleanupService {

    @Autowired
    private SlotBookingRepository slotBookingRepository;

    @Autowired
    private SlotRepository slotRepository;

    @Autowired
    private UserNotificationService userNotificationService;

    /**
     * Runs every 5 minutes to find and expire overdue bookings.
     * A booking is considered expired if:
     * - Its status is still "booked"
     * - The slot's end time has already passed
     */
    @Scheduled(fixedRate = 300000) // every 5 minutes
    @Transactional
    public void expireOverdueBookings() {
        try {
            List<SlotBooking> expiredBookings = slotBookingRepository
                    .findExpiredBookings(LocalDateTime.now());

            if (expiredBookings.isEmpty()) {
                return; // Nothing to clean up — skip logging to reduce noise
            }

            log.info("Found {} expired slot booking(s). Processing...", expiredBookings.size());

            for (SlotBooking booking : expiredBookings) {
                try {
                    expireBooking(booking);
                } catch (Exception e) {
                    // Log but continue processing other expired bookings
                    log.error("Failed to expire booking {}: {}",
                            booking.getId(), e.getMessage(), e);
                }
            }

        } catch (Exception e) {
            log.error("Slot booking cleanup job encountered an error: {}", e.getMessage(), e);
        }
    }

    /**
     * Marks a single booking as EXPIRED and releases the slot.
     */
    private void expireBooking(SlotBooking booking) {
        log.info("Expiring booking: bookingId={}, userId={}, slotId={}, slotEndTime={}",
                booking.getId(),
                booking.getUser().getId(),
                booking.getSlot().getId(),
                booking.getSlot().getEndTime());

        // 1. Mark booking as EXPIRED
        booking.setStatus(BookingStatus.EXPIRED.getValue());
        slotBookingRepository.save(booking);

        // 2. Release the slot
        Slot slot = booking.getSlot();
        slot.setBooked(false);
        slotRepository.save(slot);

        // 3. Notify user
        userNotificationService.createNotification(
                booking.getUser().getId(),
                "Booking Expired",
                "Your slot booking for "
                        + booking.getSlot().getStartTime().toLocalTime()
                        + " - "
                        + booking.getSlot().getEndTime().toLocalTime()
                        + " has expired because you did not start a charging session.",
                "BOOKING_EXPIRED");

        log.info("✅ Booking {} expired and slot {} released",
                booking.getId(), slot.getId());
    }
}
