package com.bentork.ev_system.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bentork.ev_system.model.Charger;

public interface ChargerRepository extends JpaRepository<Charger, Long> {

    List<Charger> findByStationId(Long stationId);

    Optional<Charger> findByOcppId(String ocppId);
}
