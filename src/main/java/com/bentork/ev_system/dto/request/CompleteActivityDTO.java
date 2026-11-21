package com.bentork.ev_system.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload when completing an activity with notes and optional outcome.
 * Supports flexible alias field names from various frontend implementations.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CompleteActivityDTO {

    @JsonAlias({"note", "remarks", "comment", "completionNotes", "summary", "resolution"})
    private String notes;   // Outcome/resolution notes

    @JsonAlias({"callOutcome", "status", "result"})
    private String outcome; // Optional updated call outcome (e.g., "interested", "meeting_scheduled")
}
