package com.bentork.ev_system.dto.followup;

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
public class FollowUpProgressResponse {

    private Long campaignId;
    private String campaignName;
    private String status;
    private Integer totalBatchLeads;
    private List<RoundProgressItem> waterfall;
    private String overallRetentionRate;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RoundProgressItem {
        private Integer roundNumber;
        private String status;
        private Integer eligibleCount;
        private Integer selectedCount;
        private Integer sentCount;
        private Integer deliveredCount;
        private Integer failedCount;
    }
}

