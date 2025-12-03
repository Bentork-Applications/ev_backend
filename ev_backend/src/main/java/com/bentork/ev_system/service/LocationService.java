package com.bentork.ev_system.service;

import com.bentork.ev_system.dto.request.LocationDTO;
import com.bentork.ev_system.mapper.LocationMapper;
import com.bentork.ev_system.model.Admin;
import com.bentork.ev_system.model.Location;
import com.bentork.ev_system.repository.AdminRepository;
import com.bentork.ev_system.repository.LocationRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LocationService {

    @Autowired
    private LocationRepository locationRepo;

    @Autowired
    private AdminRepository adminRepo;

    public Location addLocation(LocationDTO dto, Admin admin) {
        Location location = LocationMapper.toEntity(dto, admin);
        return locationRepo.save(location);
    }

    public List<Map<String, Object>> getAllLocationNames() {
        return locationRepo.findAll().stream()
                .map(location -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", location.getId());
                    map.put("name", location.getName());
                    return map;
                })
                .collect(Collectors.toList());
    }

}


