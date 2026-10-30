package com.bentork.ev_system.dto.response;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SalesTargetsOverviewResponse {

    private int year;
    private Integer selectedMonth;

    // Target and achievement for the selected (or current) month
    private SalesTargetResponse currentMonth;

    // Aggregated annual summary
    private YearSummary yearSummary = new YearSummary();

    // Monthly breakdown for all 12 months of the year
    private List<SalesTargetResponse> monthlyTargets = new ArrayList<>();

    // Individual sales representative targets and achievements
    private List<SalesTargetResponse> salesRepTargets = new ArrayList<>();

    @Getter
    @Setter
    public static class YearSummary {
        private Double totalTargetRevenue = 0.0;
        private Double totalAchievedRevenue = 0.0;
        private Double overallRevenueAchievementRate = 0.0;
        private Double remainingRevenue = 0.0;

        private Integer totalTargetLeads = 0;
        private Long totalAchievedLeads = 0L;
        private Double overallLeadsAchievementRate = 0.0;
        private Long remainingLeads = 0L;

        private Integer totalTargetDeals = 0;
        private Long totalAchievedDeals = 0L;
        private Double overallDealsAchievementRate = 0.0;
        private Long remainingDeals = 0L;
    }
}
