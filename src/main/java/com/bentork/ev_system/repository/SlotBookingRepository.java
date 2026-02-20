package com.bentork.ev_system.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bentork.ev_system.model.SlotBooking;

public interface SlotBookingRepository extends JpaRepository<SlotBooking, Long> {

        // Find all bookings by user
        List<SlotBooking> findByUserId(Long userId);

        // Find bookings by user and status
        List<SlotBooking> findByUserIdAndStatus(Long userId, String status);

        // Find all bookings by station
        List<SlotBooking> findByStationId(Long stationId);

        // Find all bookings by charger
        List<SlotBooking> findByChargerId(Long chargerId);

        // Find booking by slot
        Optional<SlotBooking> findBySlotId(Long slotId);

        // Check if user already has an active booking for a charger
        @Query("SELECT COUNT(sb) > 0 FROM SlotBooking sb " +
                        "WHERE sb.user.id = :userId AND sb.charger.id = :chargerId " +
                        "AND sb.status = 'booked'")
        boolean hasActiveBooking(@Param("userId") Long userId,
                        @Param("chargerId") Long chargerId);

        // Find expired bookings that need status update
        @Query("SELECT sb FROM SlotBooking sb " +
                        "WHERE sb.status = 'booked' AND sb.slot.endTime < :now")
        List<SlotBooking> findExpiredBookings(@Param("now") LocalDateTime now);

        // Find active booking for a user on a specific charger at a given time
        @Query("SELECT sb FROM SlotBooking sb " +
                        "WHERE sb.user.id = :userId AND sb.charger.id = :chargerId " +
                        "AND sb.status = 'booked' " +
                        "AND sb.slot.startTime <= :now AND sb.slot.endTime >= :now")
        Optional<SlotBooking> findActiveBookingForUserAndCharger(@Param("userId") Long userId,
                        @Param("chargerId") Long chargerId,
                        @Param("now") LocalDateTime now);

        // Count active bookings for a user
        @Query("SELECT COUNT(sb) FROM SlotBooking sb " +
                        "WHERE sb.user.id = :userId AND sb.status = 'booked'")
        long countActiveBookingsByUser(@Param("userId") Long userId);

        // Delete all bookings associated with a slot
        void deleteBySlotId(Long slotId);
}
