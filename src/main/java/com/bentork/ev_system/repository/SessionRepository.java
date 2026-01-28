package com.bentork.ev_system.repository;

import com.bentork.ev_system.model.Charger;
import com.bentork.ev_system.model.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SessionRepository extends JpaRepository<Session, Long> {
    Optional<Session> findFirstByStatusOrderByStartTimeDesc(String status);

    Optional<Session> findFirstByChargerAndStatusOrderByCreatedAtDesc(Charger charger, String status);

    boolean existsByUserIdAndStatus(Long id, String status);

    Optional<Session> findFirstByChargerAndStatusInOrderByCreatedAtDesc(
            Charger charger, List<String> statuses);

    // Find sessions by charger ID
    List<Session> findByChargerId(Long chargerId);

    // Find sessions by station ID (through charger)
    @Query("SELECT s FROM Session s WHERE s.charger.station.id = :stationId")
    List<Session> findByStationId(@Param("stationId") Long stationId);

    // Find sessions by multiple station IDs
    @Query("SELECT s FROM Session s WHERE s.charger.station.id IN :stationIds")
    List<Session> findByStationIdIn(@Param("stationIds") List<Long> stationIds);

    // Count sessions by station ID
    @Query("SELECT COUNT(s) FROM Session s WHERE s.charger.station.id = :stationId")
    Long countByStationId(@Param("stationId") Long stationId);
}
