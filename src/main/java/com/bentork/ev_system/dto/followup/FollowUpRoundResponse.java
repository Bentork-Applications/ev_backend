package com.bentork.ev_system.dto.followup;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FollowUpRoundResponse {

    private Long id;
    private Long campaignId;
    private Integer roundNumber;
    private Long parentRoundId;
    private String channel;
    private String subject;
    private String messageBody;
    private LocalDate plannedDate;
    private LocalTime plannedTime;
    private String timezone;
    private String sendMode;
    private String status;
    private Integer selectedLeadCount;
    private Integer sentCount;
    private Integer deliveredCount;
    private Integer failedCount;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String lastTriggeredByEmail;
    private Boolean isFinalRound;
    private Integer nextRoundNumber;
    private List<FollowUpAttachmentResponse> attachments;
}

