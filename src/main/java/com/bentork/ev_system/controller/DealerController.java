package com.bentork.ev_system.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bentork.ev_system.dto.request.StationDTO;
import com.bentork.ev_system.service.strategy.StationAccessStrategy;
import com.bentork.ev_system.service.strategy.StationAccessStrategyFactory;

import lombok.extern.slf4j.Slf4j;

/**
 * Controller for dealer-specific operations.
 * Dealers can only access their assigned stations.
 */
@RestController
@RequestMapping("/api/dealer")
@Slf4j
public class DealerController {

    @Autowired
    private StationAccessStrategyFactory strategyFactory;

    /**
     * Get all stations assigned to the current dealer
     * GET /api/dealer/stations
     */
    @GetMapping("/stations")
    @PreAuthorize("hasAuthority('DEALER')")
    public ResponseEntity<List<StationDTO>> getMyStations() {
        String email = getCurrentUserEmail();
        log.info("Dealer {} fetching their assigned stations", email);

        StationAccessStrategy strategy = strategyFactory.getStrategy("DEALER");
        List<StationDTO> stations = strategy.getAccessibleStations(email);

        return ResponseEntity.ok(stations);
    }

    /**
     * Get a specific station's details (only if assigned to this dealer)
     * GET /api/dealer/stations/{id}
     */
    @GetMapping("/stations/{id}")
    @PreAuthorize("hasAuthority('DEALER')")
    public ResponseEntity<?> getStationById(@PathVariable Long id) {
        String email = getCurrentUserEmail();
        log.info("Dealer {} fetching station {}", email, id);

        StationAccessStrategy strategy = strategyFactory.getStrategy("DEALER");

        if (!strategy.hasAccessToStation(email, id)) {
            log.warn("Dealer {} attempted to access unauthorized station {}", email, id);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("You do not have access to this station");
        }

        // Get the station from the list of accessible stations
        List<StationDTO> stations = strategy.getAccessibleStations(email);
        StationDTO station = stations.stream()
                .filter(s -> s.getId().equals(id))
                .findFirst()
                .orElse(null);

        if (station == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(station);
    }

    /**
     * Get count of stations assigned to the current dealer
     * GET /api/dealer/stations/count
     */
    @GetMapping("/stations/count")
    @PreAuthorize("hasAuthority('DEALER')")
    public ResponseEntity<Long> getMyStationsCount() {
        String email = getCurrentUserEmail();
        log.debug("Dealer {} fetching their station count", email);

        StationAccessStrategy strategy = strategyFactory.getStrategy("DEALER");
        Long count = strategy.getAccessibleStationsCount(email);

        return ResponseEntity.ok(count);
    }

    /**
     * Get current authenticated user's email
     */
    private String getCurrentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName();
    }
}
