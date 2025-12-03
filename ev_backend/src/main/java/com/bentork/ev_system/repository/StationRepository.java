package com.bentork.ev_system.repository;

import com.bentork.ev_system.model.Station;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StationRepository extends JpaRepository<Station, Long> {
    
    // Custom query to fetch stations by location
    List<Station> findByLocationId(Long locationId);
}
