package com.bentork.ev_system.dto.request;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateCampaignDTO {

    private Long id;
    private String name;
    private String type; // e.g., "POST", "OFFER", "NEWSLETTER", "WHATSAPP_OUTREACH", "FOLLOW_UP"
    private String channel; // "EMAIL", "WHATSAPP", "BOTH"
    private String subject;
    private String messageBody;
    private Long targetAudienceSegmentId;
    private String description;
    private String objective;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate scheduledDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    private String timezone;
    private String sendMode; // "MANUAL", "AUTOMATIC", "HYBRID"

    @JsonAlias({"ownerId", "adminId", "targetOwnerAdminId"})
    private Long ownerAdminId;

    private String status;

    @JsonAlias({"roundList", "followUpRounds", "campaignRounds"})
    private List<CampaignRoundDTO> rounds;
}
