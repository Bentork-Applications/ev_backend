package com.bentork.ev_system.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.bentork.ev_system.dto.request.StationDTO;
import com.bentork.ev_system.mapper.StationMapper;
import com.bentork.ev_system.model.Charger;
import com.bentork.ev_system.model.Location;
import com.bentork.ev_system.model.Station;
import com.bentork.ev_system.repository.ChargerRepository;
import com.bentork.ev_system.repository.LocationRepository;
import com.bentork.ev_system.repository.StationRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class StationService {

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private ChargerRepository chargerRepository;

    @Autowired
    private Clock clock;

    public StationDTO createStation(StationDTO dto) {
        try {
            Location location = locationRepository.findById(dto.getLocationId())
                    .orElseThrow(() -> new EntityNotFoundException("Location not found"));

            Station station = StationMapper.toEntity(dto);
            station.setLocation(location);

            Station saved = stationRepository.save(station);
            log.info("Station created: id={}, name={}, locationId={}",
                    saved.getId(), saved.getName(), location.getId());

            return StationMapper.toDTO(saved);
        } catch (EntityNotFoundException e) {
            log.error("Failed to create station - Location not found: locationId={}",
                    dto.getLocationId());
            throw e;
        } catch (Exception e) {
            log.error("Failed to create station: name={}, locationId={}: {}",
                    dto.getName(), dto.getLocationId(), e.getMessage(), e);
            throw e;
        }
    }

    public List<StationDTO> getAllStations() {
        try {
            List<StationDTO> stations = stationRepository.findAll().stream()
                    .map(StationMapper::toDTO)
                    .collect(Collectors.toList());

            if (log.isDebugEnabled()) {
                log.debug("Retrieved {} stations", stations.size());
            }

            return stations;
        } catch (Exception e) {
            log.error("Failed to retrieve all stations: {}", e.getMessage(), e);
            throw e;
        }
    }

    public StationDTO getStationById(Long id) {
        try {
            Station station = stationRepository.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Station not found with ID: " + id));

            if (log.isDebugEnabled()) {
                log.debug("Retrieved station: id={}, name={}", id, station.getName());
            }

            return StationMapper.toDTO(station);
        } catch (EntityNotFoundException e) {
            log.warn("Station not found: id={}", id);
            throw e;
        } catch (Exception e) {
            log.error("Failed to retrieve station: id={}: {}", id, e.getMessage(), e);
            throw e;
        }
    }

    public StationDTO updateStation(Long id, StationDTO dto) {
        try {
            Station station = stationRepository.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Station not found with ID: " + id));

            Location location = locationRepository.findById(dto.getLocationId())
                    .orElseThrow(() -> new EntityNotFoundException("Location not found"));

            String oldStatus = station.getStatus();

            station.setName(dto.getName());
            station.setLocation(location);
            station.setStatus(dto.getStatus());
            station.setDirectionLink(dto.getDirectionLink());

            Station updated = stationRepository.save(station);

            log.info("Station updated: id={}, name={}, status changed from {} to {}",
                    id, updated.getName(), oldStatus, updated.getStatus());

            return StationMapper.toDTO(updated);
        } catch (EntityNotFoundException e) {
            log.warn("Failed to update station - Entity not found: stationId={}, message={}",
                    id, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to update station: id={}: {}", id, e.getMessage(), e);
            throw e;
        }
    }

    public void deleteStation(Long id) {
        try {
            if (!stationRepository.existsById(id)) {
                throw new EntityNotFoundException("Station not found with ID: " + id);
            }
            stationRepository.deleteById(id);
            log.info("Station deleted: id={}", id);
        } catch (EntityNotFoundException e) {
            log.warn("Failed to delete station - Station not found: id={}", id);
            throw e;
        } catch (Exception e) {
            log.error("Failed to delete station: id={}: {}", id, e.getMessage(), e);
            throw e;
        }
    }

    public Long getTotalStations() {
        try {
            Long total = stationRepository.count();

            if (log.isDebugEnabled()) {
                log.debug("Total stations count: {}", total);
            }

            return total;
        } catch (Exception e) {
            log.error("Failed to get total stations count: {}", e.getMessage(), e);
            throw e;
        }
    }

    public Long getActiveStations() {
        try {
            Long activeCount = stationRepository.findAll().stream()
                    .filter(station -> "ACTIVE".equalsIgnoreCase(station.getStatus()))
                    .count();

            if (log.isDebugEnabled()) {
                log.debug("Active stations count: {}", activeCount);
            }

            return activeCount;
        } catch (Exception e) {
            log.error("Failed to get active stations count: {}", e.getMessage(), e);
            throw e;
        }
    }

    public Double getAverageUptime() {
        try {
            List<Station> stations = stationRepository.findAll();

            if (stations.isEmpty()) {
                log.warn("No stations found for uptime calculation");
                return 0.0;
            }

            double totalUptime = 0.0;
            int stationCount = 0;

            for (Station station : stations) {
                List<Charger> chargers = chargerRepository.findByStationId(station.getId());

                if (!chargers.isEmpty()) {
                    long availableChargers = chargers.stream()
                            .filter(charger -> Boolean.TRUE.equals(charger.isAvailability()))
                            .count();

                    double stationUptime = (availableChargers * 100.0) / chargers.size();
                    totalUptime += stationUptime;
                    stationCount++;
                }
            }

            double avgUptime = stationCount > 0 ? totalUptime / stationCount : 0.0;
            double roundedUptime = Math.round(avgUptime * 100.0) / 100.0;

            log.info("Average uptime calculated: {}% across {} stations",
                    roundedUptime, stationCount);

            return roundedUptime;
        } catch (Exception e) {
            log.error("Failed to calculate average uptime: {}", e.getMessage(), e);
            throw e;
        }
    }

    //Error Today
    public Long getTodaysErrorCount(){
        try {
            LocalDate today = LocalDate.now(clock);
            LocalDateTime startOfDay = today.atStartOfDay();
            LocalDateTime endOfDay = today.atTime(23, 59, 59, 999999999);

            log.debug("Getting all station to count todays errors");
            List<Station> allStations = stationRepository.findAll();

            return allStations.stream()
                    .filter(station -> station.getCreatedAt() != null
                    && (station.getCreatedAt().isEqual(startOfDay)
                    || (station.getCreatedAt().isAfter(startOfDay)
                    && station.getCreatedAt().isBefore(endOfDay))))
                    .filter(station -> station.getStatus() != null
                    && station.getStatus().toLowerCase().contains("error"))
                    .count();

        } catch (DataAccessException e) {
            log.error("Error while accessing data: {}", e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error in getTodaysErrorCount ", e);
            throw new RuntimeException("Failed to calculate today's error count", e);
        }
    }
}