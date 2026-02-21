package com.bentork.ev_system.controller;

import com.bentork.ev_system.dto.response.CafeResponseDTO;
import com.bentork.ev_system.service.GooglePlacesService;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/cafes")
public class CafeController {

    @Autowired
    private GooglePlacesService googlePlacesService;

    /**
     * Fetch nearby cafes using Google Places API.
     *
     * @param latitude  Center latitude (required)
     * @param longitude Center longitude (required)
     * @param radius    Search radius in meters (optional, default 1500m)
     * @return List of nearby cafes with name, address, location, rating, etc.
     */
    @GetMapping("/nearby")
    public ResponseEntity<?> getNearbyCafes(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "1500") double radius) {

        log.info("GET /api/cafes/nearby - lat={}, lng={}, radius={}m", latitude, longitude, radius);

        try {
            // Validate radius (Google allows max 50000 meters)
            if (radius <= 0 || radius > 50000) {
                log.warn("GET /api/cafes/nearby - Invalid radius: {}", radius);
                return ResponseEntity.badRequest()
                        .body(Collections.singletonMap("error",
                                "Radius must be between 1 and 50000 meters"));
            }

            List<CafeResponseDTO> cafes = googlePlacesService.findNearbyCafes(latitude, longitude, radius);

            log.info("GET /api/cafes/nearby - Success, returned {} cafes", cafes.size());
            return ResponseEntity.ok(cafes);

        } catch (Exception e) {
            log.error("GET /api/cafes/nearby - Failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Failed to fetch nearby cafes"));
        }
    }
}
