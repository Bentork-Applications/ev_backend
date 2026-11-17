package com.bentork.ev_system.dto.request;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateCampaignDTO {
    private String name;
    private String type; // e.g., "POST", "OFFER", "NEWSLETTER", "WHATSAPP_OUTREACH"
    private String channel; // "EMAIL", "WHATSAPP", "BOTH"
    private String subject;
    private String messageBody;
    private Long targetAudienceSegmentId;
    private String description;
    private String objective;
    private LocalDate scheduledDate;
    private LocalDate startDate;
    private LocalDate endDate;
    private String timezone;
    private String sendMode; // "MANUAL", "AUTOMATIC", "HYBRID"
    private Long ownerAdminId;
}
