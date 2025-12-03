package com.bentork.ev_system.mapper;

import com.bentork.ev_system.dto.request.EmergencyContactDTO;
import com.bentork.ev_system.model.EmergencyContact;
import com.bentork.ev_system.model.Station;

public class EmergencyContactMapper {

    public static EmergencyContact toEntity(EmergencyContactDTO dto, Station station) {
        EmergencyContact contact = new EmergencyContact();
        contact.setId(dto.getId());
        contact.setName(dto.getName());
        contact.setContactNumber(dto.getContactNumber());
        contact.setStation(station); // ✅ Important
        return contact;
    }

    public static EmergencyContactDTO toDTO(EmergencyContact contact) {
        EmergencyContactDTO dto = new EmergencyContactDTO();
        dto.setId(contact.getId());
        dto.setName(contact.getName());
        dto.setContactNumber(contact.getContactNumber());
        dto.setStationId(contact.getStation().getId()); // ✅ Required for DTO
        return dto;
    }
}
