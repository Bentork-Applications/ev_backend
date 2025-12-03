package com.bentork.ev_system.repository;


import com.bentork.ev_system.model.Revenue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RevenueRepository extends JpaRepository<Revenue, Long> {
    List<Revenue> findBySessionId(Long sessionId);
    List<Revenue> findByUserId(Long userId);
    List<Revenue> findByChargerId(Long chargerId);
    List<Revenue> findByStationId(Long stationId);
}