package com.bentork.ev_system.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import com.bentork.ev_system.dto.request.ChargerDTO;
import com.bentork.ev_system.mapper.ChargerMapper;
import com.bentork.ev_system.model.Charger;
import com.bentork.ev_system.model.Station;
import com.bentork.ev_system.repository.ChargerRepository;
import com.bentork.ev_system.repository.StationRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

import com.bentork.ev_system.service.interfaces.IChargerService;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChargerService implements IChargerService {

    private final ChargerRepository chargerRepository;

    private final StationRepository stationRepository;

    @Caching(evict = {
        @CacheEvict(value = "chargers", allEntries = true),
        @CacheEvict(value = "dashboard-stats", allEntries = true)
    })
    public String createCharger(ChargerDTO dto) {
        try {
            Station station = stationRepository.findById(dto.getStationId())
                    .orElseThrow(() -> new EntityNotFoundException("Station not found with ID: "+dto.getStationId()));
        
        Charger charger = ChargerMapper.toEntity(dto);
        charger.setStation(station);
        chargerRepository.save(charger);

        log.info("Charger created: id={}, ocppId={}", charger.getId(), charger.getOcppId(), station.getId());
        return "Charger Created";
        } catch (EntityNotFoundException e) {
            log.warn("Failed to create charger - Station not found: stationId={}", dto.getStationId());
            throw e;
         } catch (Exception e) {
            log.error("Failed to create charger: {}", e.getMessage(), e);
            throw e;
         } 
    }

    @Cacheable(value = "chargers", key = "'all-chargers'")
    public List<ChargerDTO> getAllChargers() {
        try {
            List<ChargerDTO> chargers = chargerRepository.findAll()
                .stream()
                .map(ChargerMapper::toDto)
                .collect(Collectors.toList());

                log.info("Retrived {} chargers", chargers.size());
                return chargers;
        } catch (Exception e) {
            log.error("Failed to retrieve chargers: {}", e.getMessage(), e);
            throw e;
        }   
    }

    @Cacheable(value = "chargers", key = "#id")
    public ChargerDTO getChargerById(Long id) {
        try {
            Charger charger = chargerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Charger not found with ID: " + id));

            log.info("Retrived charger: id={}, ocppId={}", charger.getId(), charger.getOcppId());
            return ChargerMapper.toDto(charger);
        } catch (EntityNotFoundException e) {
            log.warn("Charger not found: id={}", id);
            throw e;
        } catch (Exception e) {
            log.error("Failed to retrieve charger: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Caching(evict = {
        @CacheEvict(value = "chargers", allEntries = true),
        @CacheEvict(value = "dashboard-stats", allEntries = true)
    })
    public String updateCharger(Long id, ChargerDTO dto) {
        try {
            Charger charger = chargerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Charger not found with ID: " + id));

            Station station = stationRepository.findById(dto.getStationId())
                .orElseThrow(() -> new EntityNotFoundException("Station not found with ID: " + dto.getStationId()));

            String oldType = charger.getChargerType();
            charger.setStation(station);
            charger.setOcppId(dto.getOcppId());
            charger.setConnectorType(dto.getConnectorType());
            charger.setChargerType(dto.getChargerType());
            charger.setRate(dto.getRate());
            charger.setPlatformFeePerKwh(dto.getPlatformFeePerKwh() != null ? dto.getPlatformFeePerKwh() : 0.0);
            charger.setOccupied(dto.isOccupied());
            charger.setAvailability(dto.isAvailability());
            

            chargerRepository.save(charger);

            log.info("Charger updated: id={}, ocppId={}, type changed from {} to {}", id, charger.getOcppId(), oldType, charger.getChargerType());
            return "Charger Updated";
        } catch (EntityNotFoundException e) {
            log.warn("Failed to update charger - Not found: id={}, message={}", id, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to update charger: id={}: {}", id, e.getMessage(), e);
            throw e;
        }
    }

    @Caching(evict = {
        @CacheEvict(value = "chargers", allEntries = true),
        @CacheEvict(value = "dashboard-stats", allEntries = true)
    })
    public String deleteCharger(Long id) {
       try {
            if (!chargerRepository.existsById(id)) {
                throw new EntityNotFoundException("Charger not found with ID: " + id);
            }
            chargerRepository.deleteById(id);
            log.info("Charger deleted: id={}", id);
            return "Charger Deleted";
        } catch (EntityNotFoundException e) {
            log.warn("Failed to delete charger - Not found: id={}", id);
            throw e;
        } catch (Exception e) {
            log.error("Failed to delete charger: id={}: {}", id, e.getMessage(), e);
            throw e;
        }
    }

    // Total Chargers
    @Cacheable(value = "dashboard-stats", key = "'total-chargers'")
    public Long getTotalChargers() {
       try {
            Long total = chargerRepository.count();
            log.debug("Total chargers: {}", total);
            return total;
        } catch (Exception e) {
            log.error("Failed to get total chargers: {}", e.getMessage(), e);
            throw e;
        }
    }
    @Cacheable(value = "dashboard-stats", key = "'available-chargers'")
    public Long getAvailableChargers() {
        try {
            Long available = chargerRepository.countByAvailabilityTrueAndOccupiedFalse();
            log.debug("Available chargers: {}", available);
            return available;
        } catch (Exception e) {
            log.error("Failed to get available chargers: {}", e.getMessage(), e);
            throw e;
        }
    }

    // AC Chargers
    @Cacheable(value = "dashboard-stats", key = "'ac-chargers'")
    public Long getACChargers() {
        try {
            Long acCount = chargerRepository.countByChargerTypeIgnoreCase("AC");
            log.debug("Total AC chargers: {}", acCount);
            return acCount;
        } catch (Exception e) {
            log.error("Failed to get AC chargers: {}", e.getMessage(), e);
            throw e;
        }
    }

    // DC Chargers
    @Cacheable(value = "dashboard-stats", key = "'dc-chargers'")
    public Long getDCChargers() {
        try {
            Long dcCount = chargerRepository.countByChargerTypeIgnoreCase("DC");
            log.debug("Total DC chargers: {}", dcCount);
            return dcCount;
        } catch (Exception e) {
            log.error("Failed to get DC chargers: {}", e.getMessage(), e);
            throw e;
        }
    }

    // Get Charger by ocppid
    public ChargerDTO getChargerByOcppId(String ocppId) {
        try {
            Charger charger = chargerRepository.findByOcppId(ocppId).orElseThrow();
            log.debug("Chargers: {}", charger);
            return ChargerMapper.toDto(charger);
        } catch (Exception e) {
            log.error("Failed to get chargers: {}", e.getMessage(), e);
            throw e;
        }
    }
}
