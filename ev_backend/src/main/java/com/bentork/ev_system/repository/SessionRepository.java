package com.bentork.ev_system.repository;

import com.bentork.ev_system.model.Charger;
import com.bentork.ev_system.model.Session;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SessionRepository extends JpaRepository<Session, Long> {
    Optional<Session> findFirstByStatusOrderByStartTimeDesc(String status);

    Optional<Session> findFirstByChargerAndStatusOrderByCreatedAtDesc(Charger charger, String status);

    boolean existsByUserIdAndStatus(Long id, String status);

    Optional<Session> findFirstByChargerAndStatusInOrderByCreatedAtDesc(
            Charger charger, List<String> statuses);
}
