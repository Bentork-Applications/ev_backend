package com.bentork.ev_system.dto.followup;

import java.time.LocalDateTime;

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
public class FollowUpDispatchResultDTO {

    private Long campaignId;
    private Integer roundNumber;
    private String status;
    private Integer totalSelected;
    private Integer sentCount;
    private Integer failedCount;
    private Integer optedOutCount;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Integer nextRoundUnlocked;
    private String message;
}

