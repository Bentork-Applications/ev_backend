package com.bentork.ev_system.dto.followup;

import java.time.LocalDate;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for explicitly scheduling or updating the planned execution
 * date, time, and send mode for a specific round.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FollowUpRoundScheduleDTO {

    @NotNull(message = "Planned date is required")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate plannedDate;

    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime plannedTime;

    /**
     * Execution mode: "MANUAL" (default) or "AUTOMATIC".
     */
    private String sendMode;

    private String subject;

    private String messageBody;
}

