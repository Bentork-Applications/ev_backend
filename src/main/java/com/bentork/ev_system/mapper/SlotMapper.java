package com.bentork.ev_system.mapper;

import java.time.format.DateTimeFormatter;

import com.bentork.ev_system.dto.request.SlotDTO;
import com.bentork.ev_system.model.Charger;
import com.bentork.ev_system.model.Slot;

public class SlotMapper {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public static SlotDTO toDTO(Slot slot) {
        SlotDTO dto = new SlotDTO();
        dto.setId(slot.getId());
        dto.setChargerId(slot.getCharger() != null ? slot.getCharger().getId() : null);
        dto.setBooked(slot.isBooked());
        dto.setAllDay(slot.isAllDay());
        dto.setCreatedAt(slot.getCreatedAt());

        if (slot.isAllDay()) {
            // For all-day slots: return only time (e.g., "09:00"), no date
            dto.setStartTimeOnly(slot.getStartTime().format(TIME_FORMATTER));
            dto.setEndTimeOnly(slot.getEndTime().format(TIME_FORMATTER));
            dto.setStartTime(null);
            dto.setEndTime(null);
        } else {
            // For date-specific slots: return full datetime
            dto.setStartTime(slot.getStartTime());
            dto.setEndTime(slot.getEndTime());
        }

        return dto;
    }

    public static Slot toEntity(SlotDTO dto, Charger charger) {
        Slot slot = new Slot();
        slot.setCharger(charger);
        slot.setStartTime(dto.getStartTime());
        slot.setEndTime(dto.getEndTime());
        slot.setBooked(dto.isBooked());
        slot.setAllDay(dto.isAllDay());
        return slot;
    }
}
