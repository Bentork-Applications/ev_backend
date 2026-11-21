package com.bentork.ev_system.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload to reschedule an existing activity/follow-up.
 * Supports flexible alias field names from various frontend implementations.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RescheduleActivityDTO {

    @JsonAlias({"followUpDate", "date", "scheduledDate", "nextFollowUpDate", "followupDate", "rescheduleDate"})
    private String newFollowUpDate; // yyyy-MM-dd (required)

    @JsonAlias({"followUpTime", "time", "followupTime", "scheduledTime", "rescheduleTime"})
    private String newFollowUpTime; // HH:mm or HH:mm:ss (optional)

    @JsonAlias({"notes", "note", "remarks", "comment", "rescheduleReason"})
    private String reason;          // Note or reason for rescheduling

    public String getEffectiveFollowUpDate() {
        return newFollowUpDate;
    }

    public String getEffectiveFollowUpTime() {
        return newFollowUpTime;
    }

    public String getEffectiveReason() {
        return reason;
    }
}
