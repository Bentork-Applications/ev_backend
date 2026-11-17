package com.bentork.ev_system.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignResponse {
    private Long id;
    private String name;
    private String type;
    private String channel;
    private String subject;
    private String messageBody;
    private Long targetAudienceSegmentId;
    private String description;
    private String objective;
    private LocalDate scheduledDate;
    private LocalDate startDate;
    private LocalDate endDate;
    private String timezone;
    private String sendMode;
    private String status;
    private Long ownerAdminId;
    private String ownerAdminName;
    private String ownerAdminEmail;

    private Integer totalRounds;
    private Integer completedRounds;
    private Integer totalRecipients;
    private Integer sentCount;
    private Integer deliveredCount;
    private Integer readCount;
    private Integer openedCount;
    private Integer respondedCount;
    private Integer failedCount;

    private String pauseReason;
    private LocalDateTime pausedAt;
    private String pausedByAdminEmail;

    private String createdByAdminEmail;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
