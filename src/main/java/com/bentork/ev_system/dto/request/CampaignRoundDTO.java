package com.bentork.ev_system.dto.request;

import java.time.LocalDate;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing round details within a sequential follow-up campaign.
 * Used for saving round message configurations and schedules across all 5 cascading rounds.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CampaignRoundDTO {

    private Long id;
    private Integer roundNumber; // 1, 2, 3, 4, 5
    private String channel;      // "WHATSAPP", "EMAIL", "BOTH"
    private String subject;      // Email subject or round title
    private String messageBody;  // Message body with placeholders
    private String templateName;
    private String templateBody;
    private Long templateId;
    private String templateStatus;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate plannedDate;

    @JsonFormat(pattern = "HH:mm[:ss]")
    private LocalTime plannedTime;

    private String timezone;
    private String sendMode;     // "MANUAL", "AUTOMATIC"
    private String status;       // "PENDING", "CONFIGURED", "SCHEDULED", "IN_PROGRESS", "COMPLETED", "FAILED"
}
