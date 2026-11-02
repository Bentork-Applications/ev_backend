package com.bentork.ev_system.dto.followup;

import java.time.LocalDate;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload when triggering a round send, containing optional scheduling
 * options for the subsequent round (e.g. scheduling Round 2 before sending Round 1).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FollowUpRoundSendRequestDTO {

    /**
     * Planned date for the next round (e.g., 2026-11-05).
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate nextRoundPlannedDate;

    /**
     * Planned time of day for the next round (e.g., 10:30:00).
     */
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime nextRoundPlannedTime;

    /**
     * Execution mode for the next round: "MANUAL" (default) or "AUTOMATIC".
     */
    private String nextRoundSendMode;

    /**
     * Optional pre-configured subject for the next round.
     */
    private String nextRoundSubject;

    /**
     * Optional pre-configured message body for the next round.
     */
    private String nextRoundMessageBody;
}

