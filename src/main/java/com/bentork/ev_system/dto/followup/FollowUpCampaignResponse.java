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
public class FollowUpCampaignResponse {

    private Long id;
    private String name;
    private Long batchId;
    private String batchNumber;
    private String originalFileName;
    private Integer totalBatchLeads;
    private String channel;
    private String status;
    private Integer currentRoundNumber;
    private Integer totalRounds;
    private Integer completedRounds;
    private Long ownerAdminId;
    private String ownerAdminName;
    private String createdByAdminEmail;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<FollowUpRoundResponse> rounds;
}

