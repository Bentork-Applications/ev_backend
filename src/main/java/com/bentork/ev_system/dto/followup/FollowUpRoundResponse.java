package com.bentork.ev_system.dto.followup;

import java.time.LocalDateTime;
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
    private String status;
    private Integer selectedLeadCount;
    private Integer sentCount;
    private Integer deliveredCount;
    private Integer failedCount;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String lastTriggeredByEmail;
    private List<FollowUpAttachmentResponse> attachments;
}

